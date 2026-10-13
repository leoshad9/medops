package com.medops.billing.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medops.billing.api.dto.RecordPaymentRequest;
import com.medops.billing.api.dto.StripeCheckoutSessionRequest;
import com.medops.billing.api.dto.StripeCheckoutSessionResult;
import com.medops.billing.api.dto.StripeWebhookEvent;
import com.medops.billing.domain.InvoiceStatus;
import com.medops.billing.domain.PaymentMethod;
import com.medops.billing.infrastructure.Invoice;
import com.medops.billing.infrastructure.InvoiceItem;
import com.medops.billing.infrastructure.Payment;
import com.medops.billing.infrastructure.PaymentRepository;
import com.medops.billing.infrastructure.StripeClient;
import com.medops.billing.infrastructure.StripeProperties;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.InvalidRequestException;
import com.medops.shared.exception.ServiceUnavailableException;

/**
 * Unit tests for the Stripe Checkout flow. {@link StripeClient} is mocked so no
 * HTTP call reaches Stripe; the fake keys in the test profile are never used.
 */
@ExtendWith(MockitoExtension.class)
class StripePaymentServiceTest {

    private static final UUID INVOICE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PATIENT_PROFILE_ID = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final UUID ACTOR_USER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String ACTOR_EMAIL = "patient@medops.dev";
    private static final String SESSION_ID = "cs_test_checkout123";
    private static final String CLIENT_SECRET = "cs_test_checkout123_secret_abc";

    @Mock
    private InvoiceService invoiceService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private StripeClient stripeClient;
    @Mock
    private AuditService auditService;

    private StripePaymentService service;

    @BeforeEach
    void setUp() {
        StripeProperties props = new StripeProperties(
                "sk_test_123", "pk_test_pub", "whsec_sig", "inr",
                "http://localhost:5173/patient/billing");
        service = new StripePaymentService(
                invoiceService, paymentService, paymentRepository, stripeClient,
                props, auditService);
    }
    // --- createCheckoutSession ---

    @Test
    void createCheckoutSession_chargesOutstandingBalance_andReturnsClientSecret() {
        stubPayableInvoice(5000);
        when(paymentService.completedPaidCents(INVOICE_ID)).thenReturn(1500L);
        when(stripeClient.createCheckoutSession(any())).thenReturn(new StripeCheckoutSessionResult(
                SESSION_ID, CLIENT_SECRET, 3500L, "inr",
                new StripeCheckoutSessionResult.Metadata(
                        INVOICE_ID.toString(), PATIENT_PROFILE_ID.toString(),
                        ACTOR_USER_ID.toString(), ACTOR_EMAIL)));

        var response = service.createCheckoutSession(INVOICE_ID, PATIENT_PROFILE_ID, ACTOR_USER_ID, ACTOR_EMAIL);

        assertThat(response.clientSecret()).isEqualTo(CLIENT_SECRET);
        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.amountCents()).isEqualTo(3500L);
        assertThat(response.currency()).isEqualTo("inr");
        assertThat(response.publishableKey()).isEqualTo("pk_test_pub");

        ArgumentCaptor<StripeCheckoutSessionRequest> requestCaptor =
                ArgumentCaptor.forClass(StripeCheckoutSessionRequest.class);
        verify(stripeClient).createCheckoutSession(requestCaptor.capture());
        StripeCheckoutSessionRequest request = requestCaptor.getValue();
        assertThat(request.amount()).isEqualTo(3500L);
        assertThat(request.currency()).isEqualTo("inr");
        assertThat(request.idempotencyKey()).startsWith("cs_" + INVOICE_ID + "_");
        assertThat(request.metadata())
                .containsEntry("invoice_id", INVOICE_ID.toString())
                .containsEntry("patient_profile_id", PATIENT_PROFILE_ID.toString())
                .containsEntry("actor_user_id", ACTOR_USER_ID.toString());

        verify(auditService).recordEvent(
                AuditEventType.STRIPE_CHECKOUT_SESSION_CREATED, ACTOR_USER_ID, ACTOR_EMAIL);
    }

    @Test
    void createCheckoutSession_rejectsInvoiceOfAnotherPatient() {
        when(invoiceService.payableTotal(eq(INVOICE_ID), any(UUID.class)))
                .thenThrow(new InvalidRequestException("Invoice does not belong to this patient"));
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() ->
                service.createCheckoutSession(INVOICE_ID, stranger, ACTOR_USER_ID, ACTOR_EMAIL))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invoice does not belong to this patient");

        verify(stripeClient, never()).createCheckoutSession(any());
    }

    @Test
    void createCheckoutSession_rejectsFullyPaidInvoice() {
        stubPayableInvoice(5000);
        when(paymentService.completedPaidCents(INVOICE_ID)).thenReturn(5000L);

        assertThatThrownBy(() ->
                service.createCheckoutSession(INVOICE_ID, PATIENT_PROFILE_ID, ACTOR_USER_ID, ACTOR_EMAIL))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invoice has no outstanding balance");

        verify(stripeClient, never()).createCheckoutSession(any());
    }

    @Test
    void createCheckoutSession_rejectsNonPayableInvoice() {
        when(invoiceService.payableTotal(INVOICE_ID, PATIENT_PROFILE_ID))
                .thenThrow(new InvalidRequestException(
                        "A VOID invoice cannot accept payments"));

        assertThatThrownBy(() ->
                service.createCheckoutSession(INVOICE_ID, PATIENT_PROFILE_ID, ACTOR_USER_ID, ACTOR_EMAIL))
                .isInstanceOf(InvalidRequestException.class);

        verify(stripeClient, never()).createCheckoutSession(any());
    }

    @Test
    void createCheckoutSession_mapsStripeFailuresToServiceUnavailable() {
        stubPayableInvoice(5000);
        when(paymentService.completedPaidCents(INVOICE_ID)).thenReturn(0L);
        when(stripeClient.createCheckoutSession(any()))
                .thenThrow(new ServiceUnavailableException("Payment gateway error. Please try again."));

        assertThatThrownBy(() ->
                service.createCheckoutSession(INVOICE_ID, PATIENT_PROFILE_ID, ACTOR_USER_ID, ACTOR_EMAIL))
                .isInstanceOf(ServiceUnavailableException.class);
    }
    // --- webhook: checkout.session.completed ---

    @Test
    void webhookCheckoutCompleted_recordsPaymentWithCapturedAmount() {
        when(paymentRepository.findByReference(SESSION_ID)).thenReturn(Optional.empty());
        succeededCheckoutEvent(Map.of(
                "invoice_id", INVOICE_ID.toString(),
                "actor_user_id", ACTOR_USER_ID.toString()));

        service.handleWebhook("payload", "t=sig");

        ArgumentCaptor<RecordPaymentRequest> paymentCaptor =
                ArgumentCaptor.forClass(RecordPaymentRequest.class);
        verify(paymentService).recordPayment(
                eq(INVOICE_ID), eq(SESSION_ID), paymentCaptor.capture(),
                eq(ACTOR_USER_ID), eq("stripe-webhook"));
        RecordPaymentRequest payment = paymentCaptor.getValue();
        assertThat(payment.amountCents()).isEqualTo(5000L);
        assertThat(payment.method()).isEqualTo(PaymentMethod.STRIPE);
        assertThat(payment.reference()).isEqualTo(SESSION_ID);

        verify(auditService).recordEvent(
                AuditEventType.STRIPE_PAYMENT_SUCCEEDED, ACTOR_USER_ID, "stripe-webhook");
    }

    @Test
    void webhookCheckoutCompleted_stillSupportsLegacyPaymentIntentSucceeded() {
        when(paymentRepository.findByReference(SESSION_ID)).thenReturn(Optional.empty());
        var event = new StripeWebhookEvent(
                "payment_intent.succeeded", SESSION_ID, 5000L, "inr", Map.of(
                        "invoice_id", INVOICE_ID.toString(),
                        "actor_user_id", ACTOR_USER_ID.toString()));
        when(stripeClient.constructEvent("payload", "t=sig")).thenReturn(event);

        service.handleWebhook("payload", "t=sig");

        verify(paymentService).recordPayment(
                eq(INVOICE_ID), eq(SESSION_ID), any(RecordPaymentRequest.class),
                eq(ACTOR_USER_ID), eq("stripe-webhook"));
    }

    @Test
    void webhookCheckoutCompleted_acksNonRetryableOverpayWith200Semantics() {
        when(paymentRepository.findByReference(SESSION_ID)).thenReturn(Optional.empty());
        succeededCheckoutEvent(Map.of(
                "invoice_id", INVOICE_ID.toString(),
                "actor_user_id", ACTOR_USER_ID.toString()));
        when(paymentService.recordPayment(eq(INVOICE_ID), eq(SESSION_ID), any(), any(), any()))
                .thenThrow(new InvalidRequestException("Payment exceeds outstanding balance"));

        service.handleWebhook("payload", "t=sig");

        // Must not propagate: controller acks 200 so Stripe stops retrying
        verify(auditService, never()).recordEvent(eq(AuditEventType.STRIPE_PAYMENT_SUCCEEDED), any(), any());
    }

    @Test
    void webhookCheckoutCompleted_acksMalformedInvoiceId() {
        when(paymentRepository.findByReference(SESSION_ID)).thenReturn(Optional.empty());
        succeededCheckoutEvent(Map.of("invoice_id", "not-a-uuid", "actor_user_id", ACTOR_USER_ID.toString()));

        service.handleWebhook("payload", "t=sig");

        verify(paymentService, never()).recordPayment(any(), any(), any(), any(), any());
    }

    @Test
    void webhookCheckoutCompleted_fallsBackToInvoicePatientWithoutActorMetadata() {
        when(paymentRepository.findByReference(SESSION_ID)).thenReturn(Optional.empty());
        succeededCheckoutEvent(Map.of("invoice_id", INVOICE_ID.toString()));
        when(invoiceService.requireInvoice(INVOICE_ID)).thenReturn(payableInvoice());

        service.handleWebhook("payload", "t=sig");

        verify(paymentService).recordPayment(
                eq(INVOICE_ID), eq(SESSION_ID), any(RecordPaymentRequest.class),
                eq(PATIENT_PROFILE_ID), eq("patient-" + PATIENT_PROFILE_ID));
    }

    @Test
    void webhookCheckoutCompleted_ignoresDuplicateSession() {
        when(paymentRepository.findByReference(SESSION_ID)).thenReturn(Optional.of(new Payment()));
        succeededCheckoutEvent(Map.of(
                "invoice_id", INVOICE_ID.toString(),
                "actor_user_id", ACTOR_USER_ID.toString()));

        service.handleWebhook("payload", "t=sig");

        verify(paymentService, never()).recordPayment(any(), any(), any(), any(), any());
    }

    @Test
    void webhookCheckoutCompleted_ignoresEventWithoutInvoiceMetadata() {
        when(paymentRepository.findByReference(SESSION_ID)).thenReturn(Optional.empty());
        succeededCheckoutEvent(Map.of("actor_user_id", ACTOR_USER_ID.toString()));

        service.handleWebhook("payload", "t=sig");

        verify(paymentService, never()).recordPayment(any(), any(), any(), any(), any());
    }

    @Test
    void webhookCheckoutCompleted_ignoresEventWithoutAmount() {
        when(paymentRepository.findByReference(SESSION_ID)).thenReturn(Optional.empty());
        var event = new StripeWebhookEvent(
                "checkout.session.completed", SESSION_ID, null, "inr",
                Map.of("invoice_id", INVOICE_ID.toString(),
                        "actor_user_id", ACTOR_USER_ID.toString()));
        when(stripeClient.constructEvent("payload", "t=sig")).thenReturn(event);

        service.handleWebhook("payload", "t=sig");

        verify(paymentService, never()).recordPayment(any(), any(), any(), any(), any());
    }

    @Test
    void webhookPaymentFailed_recordsNothing() {
        var event = new StripeWebhookEvent(
                "payment_intent.payment_failed", SESSION_ID, 5000L, "inr", Map.of());
        when(stripeClient.constructEvent("payload", "t=sig")).thenReturn(event);

        service.handleWebhook("payload", "t=sig");

        verify(paymentService, never()).recordPayment(any(), any(), any(), any(), any());
    }

    @Test
    void webhookUnhandledType_recordsNothing() {
        var event = new StripeWebhookEvent("charge.succeeded", SESSION_ID, 5000L, "inr", Map.of());
        when(stripeClient.constructEvent("payload", "t=sig")).thenReturn(event);

        service.handleWebhook("payload", "t=sig");

        verify(paymentService, never()).recordPayment(any(), any(), any(), any(), any());
    }

    // --- fixtures ---

    private void succeededCheckoutEvent(Map<String, String> metadata) {
        var event = new StripeWebhookEvent(
                "checkout.session.completed", SESSION_ID, 5000L, "inr", metadata);
        when(stripeClient.constructEvent("payload", "t=sig")).thenReturn(event);
    }

    private void stubPayableInvoice(long totalCents) {
        when(invoiceService.payableTotal(INVOICE_ID, PATIENT_PROFILE_ID)).thenReturn(totalCents);
    }

    private Invoice payableInvoice() {
        return invoiceWith(InvoiceStatus.ISSUED, 5000);
    }

    private Invoice invoiceWith(InvoiceStatus status, long totalCents) {
        return Invoice.builder()
                .id(INVOICE_ID)
                .patientProfileId(PATIENT_PROFILE_ID)
                .status(status)
                .items(java.util.List.of(InvoiceItem.builder()
                        .description("Consultation")
                        .quantity(1)
                        .unitPriceCents(totalCents)
                        .build()))
                .build();
    }
}
