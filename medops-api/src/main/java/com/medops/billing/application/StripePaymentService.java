package com.medops.billing.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.billing.api.dto.RecordPaymentRequest;
import com.medops.billing.api.dto.StripeCheckoutSessionRequest;
import com.medops.billing.api.dto.StripeCheckoutSessionResponse;
import com.medops.billing.api.dto.StripeCheckoutSessionResult;
import com.medops.billing.api.dto.StripeWebhookEvent;
import com.medops.billing.domain.PaymentMethod;
import com.medops.billing.infrastructure.Invoice;
import com.medops.billing.infrastructure.Payment;
import com.medops.billing.infrastructure.PaymentRepository;
import com.medops.billing.infrastructure.StripeClient;
import com.medops.billing.infrastructure.StripeProperties;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentService {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final StripeClient stripeClient;
    private final StripeProperties stripeProperties;
    private final AuditService auditService;

    /**
     * Tx 1: validate invoice and read balance - transaction commits before HTTP call.
     * External HTTP call: create Stripe Checkout Session outside any transaction.
     */
    public StripeCheckoutSessionResponse createCheckoutSession(UUID invoiceId, UUID patientProfileId,
            UUID actorUserId, String actorEmail) {

        // Tx 1 - read and validate; commits on return
        long balance = validateAndGetBalance(invoiceId, patientProfileId);
        // Per-attempt idempotency key - unique so retries after failure get a fresh session
        String idempotencyKey = "cs_" + invoiceId + "_" + UUID.randomUUID();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("invoice_id", invoiceId.toString());
        metadata.put("patient_profile_id", patientProfileId.toString());
        metadata.put("actor_user_id", actorUserId.toString());

        StripeCheckoutSessionRequest request = new StripeCheckoutSessionRequest(
                balance, stripeProperties.currency(), "Invoice " + invoiceId, idempotencyKey, metadata);

        // External HTTP call - no open transaction
        StripeCheckoutSessionResult result = stripeClient.createCheckoutSession(request);

        auditService.recordEvent(AuditEventType.STRIPE_CHECKOUT_SESSION_CREATED, actorUserId, actorEmail);

        return new StripeCheckoutSessionResponse(
                result.clientSecret(),
                result.id(),
                balance,
                stripeProperties.currency(),
                stripeProperties.publishableKey());
    }

    public void handleWebhook(String payload, String sigHeader) {
        StripeWebhookEvent event = stripeClient.constructEvent(payload, sigHeader);
        log.info("Stripe webhook: type={} referenceId={}", event.type(), event.paymentIntentId());

        switch (event.type()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" ->
                log.warn("Stripe payment failed: referenceId={}", event.paymentIntentId());
            case "checkout.session.expired" ->
                log.info("Stripe checkout session expired: referenceId={}", event.paymentIntentId());
            default -> log.debug("Unhandled Stripe event type: {}", event.type());
        }
    }

    @Transactional
    public void handleCheckoutSessionCompleted(StripeWebhookEvent event) {
        settle(event, "checkout.session.completed");
    }

    @Transactional
    public void handlePaymentIntentSucceeded(StripeWebhookEvent event) {
        settle(event, "payment_intent.succeeded");
    }

    private void settle(StripeWebhookEvent event, String eventType) {
        String referenceId = event.paymentIntentId();

        Optional<Payment> existing = paymentRepository.findByReference(referenceId);
        if (existing.isPresent()) {
            log.info("Duplicate {} ignored: referenceId={}", eventType, referenceId);
            return;
        }

        String invoiceIdStr = event.metadata().get("invoice_id");
        if (invoiceIdStr == null) {
            log.warn("No invoice_id in {} metadata: referenceId={}", eventType, referenceId);
            return;
        }

        UUID invoiceId;
        try {
            invoiceId = UUID.fromString(invoiceIdStr);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid invoice_id in {} metadata: referenceId={} invoiceId={}",
                    eventType, referenceId, invoiceIdStr);
            return;
        }
        String actorUserIdStr = event.metadata().get("actor_user_id");
        UUID actorUserId = null;
        if (actorUserIdStr != null) {
            try {
                actorUserId = UUID.fromString(actorUserIdStr);
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid actor_user_id in {} metadata: referenceId={}", eventType, referenceId);
            }
        }

        // Use the amount Stripe actually captured, not a recalculated balance
        if (event.amount() == null || event.amount() <= 0) {
            log.warn("Webhook missing amount: {} referenceId={}", eventType, referenceId);
            return;
        }

        // Fallback to invoice patient user if actor_user_id not in metadata
        UUID finalActorUserId = actorUserId;
        String finalActorEmail = "stripe-webhook";
        if (finalActorUserId == null) {
            Invoice invoice = invoiceService.requireInvoice(invoiceId);
            finalActorUserId = invoice.getPatientProfileId();
            finalActorEmail = "patient-" + invoice.getPatientProfileId();
        }

        var recordRequest = new RecordPaymentRequest(
                event.amount(), PaymentMethod.STRIPE, referenceId);

        try {
            paymentService.recordPayment(invoiceId, referenceId, recordRequest, finalActorUserId, finalActorEmail);
        } catch (InvalidRequestException ex) {
            // Non-retryable business state: ack 200 so Stripe stops retrying
            log.warn("Ignoring non-retryable {}: referenceId={} reason={}", eventType, referenceId, ex.getMessage());
            return;
        }
        auditService.recordEvent(AuditEventType.STRIPE_PAYMENT_SUCCEEDED, finalActorUserId, finalActorEmail);
    }
    // --- private ---

    /**
     * Validates the invoice and computes the outstanding balance outside any
     * transaction of this service: payableTotal and completedPaidCents each run
     * in their own read-only transaction that commits on return, so the
     * external Stripe HTTP call below stays transaction-free.
     */
    private long validateAndGetBalance(UUID invoiceId, UUID patientProfileId) {
        long total = invoiceService.payableTotal(invoiceId, patientProfileId);
        long balance = total - paymentService.completedPaidCents(invoiceId);
        if (balance <= 0) {
            throw new InvalidRequestException("Invoice has no outstanding balance");
        }
        return balance;
    }
}
