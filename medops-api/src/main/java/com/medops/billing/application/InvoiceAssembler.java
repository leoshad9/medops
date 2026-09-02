package com.medops.billing.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.medops.billing.api.dto.InvoiceResponse;
import com.medops.billing.api.dto.InvoiceResponse.InvoiceItemResponse;
import com.medops.billing.api.dto.PaymentResponse;
import com.medops.billing.domain.PaymentStatus;
import com.medops.billing.infrastructure.Invoice;
import com.medops.billing.infrastructure.Payment;
import com.medops.billing.infrastructure.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class InvoiceAssembler {

    private final PaymentRepository paymentRepository;

    public InvoiceResponse toResponse(Invoice invoice) {
        long paidCents = paymentRepository.sumAmountCentsByInvoiceIdAndStatus(
                invoice.getId(), PaymentStatus.COMPLETED);
        long totalCents = invoice.totalCents();

        List<InvoiceItemResponse> items = invoice.getItems().stream()
                .map(i -> new InvoiceItemResponse(
                        i.getId(),
                        i.getDescription(),
                        i.getQuantity(),
                        i.getUnitPriceCents(),
                        i.getUnitPriceCents() * i.getQuantity()))
                .toList();

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getPatientProfileId(),
                invoice.getAppointmentId(),
                invoice.getStatus(),
                totalCents,
                paidCents,
                totalCents - paidCents,
                invoice.getDueDate(),
                invoice.getNotes(),
                items,
                invoice.getCreatedAt());
    }

    public PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getInvoice().getId(),
                payment.getAmountCents(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getReference(),
                payment.getPaidAt(),
                payment.getCreatedAt());
    }
}
