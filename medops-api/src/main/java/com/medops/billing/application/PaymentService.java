package com.medops.billing.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.billing.api.dto.PaymentResponse;
import com.medops.billing.api.dto.RecordPaymentRequest;
import com.medops.billing.domain.InvoicePolicy;
import com.medops.billing.domain.InvoiceStatus;
import com.medops.billing.domain.PaymentStatus;
import com.medops.billing.infrastructure.Invoice;
import com.medops.billing.infrastructure.Payment;
import com.medops.billing.infrastructure.PaymentRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ConflictException;
import com.medops.shared.exception.InvalidRequestException;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final InvoiceAssembler assembler;
    private final AuditService auditService;

    @Transactional
    public PaymentResponse recordPayment(
            UUID invoiceId,
            String idempotencyKey,
            RecordPaymentRequest request,
            UUID actorUserId,
            String actorEmail) {

        Invoice invoice = invoiceService.requireInvoice(invoiceId);
        InvoicePolicy.requirePayable(invoice.getStatus());

        long paidSoFar = paymentRepository.sumAmountCentsByInvoiceIdAndStatus(invoiceId, PaymentStatus.COMPLETED);
        long remaining = invoice.totalCents() - paidSoFar;
        if (request.amountCents() > remaining) {
            throw new InvalidRequestException(
                    "Payment of " + request.amountCents() + " cents exceeds outstanding balance of " + remaining);
        }

        Payment payment;
        try {
            payment = paymentRepository.saveAndFlush(Payment.builder()
                    .invoice(invoice)
                    .idempotencyKey(idempotencyKey)
                    .amountCents(request.amountCents())
                    .method(request.method())
                    .status(PaymentStatus.COMPLETED)
                    .reference(request.reference())
                    .paidAt(Instant.now())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A payment with this Idempotency-Key already exists", ex);
        }

        updateInvoiceStatus(invoice, paidSoFar + request.amountCents());
        auditService.recordEvent(AuditEventType.PAYMENT_RECORDED, actorUserId, actorEmail);
        return assembler.toPaymentResponse(payment);
    }

    @Transactional
    public PaymentResponse refund(UUID paymentId, UUID actorUserId, String actorEmail) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new InvalidRequestException("Only a COMPLETED payment can be refunded");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        Invoice invoice = payment.getInvoice();
        long paidAfterRefund = paymentRepository.sumAmountCentsByInvoiceIdAndStatus(
                invoice.getId(), PaymentStatus.COMPLETED);
        updateInvoiceStatus(invoice, paidAfterRefund);

        auditService.recordEvent(AuditEventType.PAYMENT_REFUNDED, actorUserId, actorEmail);
        return assembler.toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForInvoice(UUID invoiceId) {
        invoiceService.requireInvoice(invoiceId);
        return paymentRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)
                .stream()
                .map(assembler::toPaymentResponse)
                .toList();
    }

    private void updateInvoiceStatus(Invoice invoice, long totalPaidCents) {
        long total = invoice.totalCents();
        InvoiceStatus next;
        if (totalPaidCents <= 0) {
            next = InvoiceStatus.ISSUED;
        } else if (totalPaidCents >= total) {
            next = InvoiceStatus.PAID;
        } else {
            next = InvoiceStatus.PARTIALLY_PAID;
        }
        invoice.setStatus(next);
    }
}
