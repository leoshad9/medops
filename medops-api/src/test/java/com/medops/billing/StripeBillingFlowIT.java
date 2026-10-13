package com.medops.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medops.auth.security.principal.MedOpsUser;
import com.medops.auth.infrastructure.repository.UserRepository;
import com.medops.billing.api.dto.StripeCheckoutSessionResult;
import com.medops.billing.api.dto.StripeWebhookEvent;
import com.medops.billing.domain.InvoiceStatus;
import com.medops.billing.domain.PaymentMethod;
import com.medops.billing.domain.PaymentStatus;
import com.medops.billing.infrastructure.Invoice;
import com.medops.billing.infrastructure.InvoiceItem;
import com.medops.billing.infrastructure.InvoiceRepository;
import com.medops.billing.infrastructure.Payment;
import com.medops.billing.infrastructure.PaymentRepository;
import com.medops.billing.infrastructure.StripeClient;
import com.medops.patients.domain.Gender;
import com.medops.patients.application.PatientRegistrationService;
import com.medops.patients.api.dto.RegisterPatientRequest;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;

/**
 * End-to-end Checkout flow over HTTP against the real test database:
 * checkout-session creation through BillingController, then the Stripe
 * webhook that records the captured payment and flips the invoice to PAID.
 * Only StripeClient is mocked - it is the external HTTP boundary.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StripeBillingFlowIT {

    private static final String PUBLISHABLE_KEY = "pk_test_doesnotmatter";
    private static final String SESSION_ID = "cs_it_checkout123";
    private static final String CLIENT_SECRET = SESSION_ID + "_secret_it";
    private static final long TOTAL_CENTS = 5000L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PatientRegistrationService patientRegistrationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatientProfileRepository patientProfileRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private StripeClient stripeClient;
    @Test
    void checkoutSessionThenWebhook_paysInvoiceEndToEnd() throws Exception {
        String email = "stripe-it-" + UUID.randomUUID() + "@medops.dev";
        PatientProfile patient = registerPatient(email);
        Invoice invoice = saveInvoice(patient.getId(), InvoiceStatus.ISSUED);

        when(stripeClient.createCheckoutSession(any())).thenReturn(new StripeCheckoutSessionResult(
                SESSION_ID, CLIENT_SECRET, TOTAL_CENTS, "inr",
                new StripeCheckoutSessionResult.Metadata(
                        invoice.getId().toString(), patient.getId().toString(),
                        patient.getUserId().toString(), email)));

        mockMvc.perform(post("/api/v1/patients/{patientId}/billing/invoices/{invoiceId}/stripe/checkout-session",
                        patient.getId(), invoice.getId())
                        .with(user(patientPrincipal(email, patient)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.clientSecret").value(CLIENT_SECRET))
                .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.data.amountCents").value(TOTAL_CENTS))
                .andExpect(jsonPath("$.data.currency").value("inr"))
                .andExpect(jsonPath("$.data.publishableKey").value(PUBLISHABLE_KEY));

        when(stripeClient.constructEvent(any(String.class), any(String.class)))
                .thenReturn(new StripeWebhookEvent(
                        "checkout.session.completed", SESSION_ID, TOTAL_CENTS, "inr",
                        Map.of("invoice_id", invoice.getId().toString(),
                                "actor_user_id", patient.getUserId().toString())));

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", "t=it-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Payment payment = paymentRepository.findByReference(SESSION_ID).orElseThrow();
        assertThat(payment.getAmountCents()).isEqualTo(TOTAL_CENTS);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.STRIPE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getIdempotencyKey()).isEqualTo(SESSION_ID);

        Invoice paid = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // Stripe retries webhook delivery; the second event must be idempotent.
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", "t=it-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        assertThat(paymentRepository.findByReference(SESSION_ID)).isPresent();
        assertThat(paymentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoice.getId())).hasSize(1);
    }
    @Test
    void checkoutSession_rejectsInvoiceOfAnotherPatient() throws Exception {
        PatientProfile owner = registerPatient("stripe-owner-" + UUID.randomUUID() + "@medops.dev");
        String strangerEmail = "stripe-stranger-" + UUID.randomUUID() + "@medops.dev";
        PatientProfile stranger = registerPatient(strangerEmail);
        Invoice invoice = saveInvoice(owner.getId(), InvoiceStatus.ISSUED);

        mockMvc.perform(post("/api/v1/patients/{patientId}/billing/invoices/{invoiceId}/stripe/checkout-session",
                        stranger.getId(), invoice.getId())
                        .with(user(patientPrincipal(strangerEmail, stranger)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(400))
                .andExpect(jsonPath("$.error.message").value("Invoice does not belong to this patient"));
    }

    @Test
    void checkoutSession_rejectsVoidInvoice() throws Exception {
        String email = "stripe-void-" + UUID.randomUUID() + "@medops.dev";
        PatientProfile patient = registerPatient(email);
        Invoice invoice = saveInvoice(patient.getId(), InvoiceStatus.VOID);

        mockMvc.perform(post("/api/v1/patients/{patientId}/billing/invoices/{invoiceId}/stripe/checkout-session",
                        patient.getId(), invoice.getId())
                        .with(user(patientPrincipal(email, patient)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_rejectsBadSignature() throws Exception {
        when(stripeClient.constructEvent(any(String.class), any(String.class)))
                .thenThrow(new com.medops.shared.exception.InvalidRequestException("Invalid webhook signature"));

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", "t=forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- fixtures ---

    protected PatientProfile registerPatient(String email) {
        patientRegistrationService.registerPatient(new RegisterPatientRequest(
                email, "Password123!", "Stripe IT", LocalDate.of(1990, 1, 1),
                Gender.FEMALE, "+12345678901"));
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        return patientProfileRepository.findByUserId(userId).orElseThrow();
    }

    protected Invoice saveInvoice(UUID patientProfileId, InvoiceStatus status) {
        Invoice invoice = Invoice.builder()
                .patientProfileId(patientProfileId)
                .status(status)
                .items(new java.util.ArrayList<>())
                .build();
        InvoiceItem item = InvoiceItem.builder()
                .description("Consultation")
                .quantity(1)
                .unitPriceCents(TOTAL_CENTS)
                .invoice(invoice)
                .build();
        invoice.setItems(List.of(item));
        return invoiceRepository.saveAndFlush(invoice);
    }

    private static MedOpsUser patientPrincipal(String email, PatientProfile patient) {
        return new MedOpsUser(patient.getUserId(), email, "irrelevant", "PATIENT",
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")), true, true);
    }
}
