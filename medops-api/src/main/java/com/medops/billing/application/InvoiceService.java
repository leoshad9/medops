package com.medops.billing.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.billing.api.dto.AddInvoiceItemRequest;
import com.medops.billing.api.dto.CreateInvoiceRequest;
import com.medops.billing.api.dto.InvoiceResponse;
import com.medops.billing.domain.InvoicePolicy;
import com.medops.billing.domain.InvoiceStatus;
import com.medops.billing.infrastructure.Invoice;
import com.medops.billing.infrastructure.InvoiceItem;
import com.medops.billing.infrastructure.InvoiceRepository;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final InvoiceAssembler assembler;
    private final AuditService auditService;

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request, UUID actorUserId, String actorEmail) {
        patientProfileRepository.findById(request.patientProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .patientProfileId(request.patientProfileId())
                .appointmentId(request.appointmentId())
                .status(InvoiceStatus.DRAFT)
                .dueDate(request.dueDate())
                .notes(request.notes())
                .build());

        auditService.recordEvent(AuditEventType.INVOICE_CREATED, actorUserId, actorEmail);
        return assembler.toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse addItem(UUID invoiceId, AddInvoiceItemRequest request) {
        Invoice invoice = requireInvoice(invoiceId);
        InvoicePolicy.requireItemAddable(invoice.getStatus());

        InvoiceItem item = InvoiceItem.builder()
                .invoice(invoice)
                .description(request.description().trim())
                .quantity(request.quantity())
                .unitPriceCents(request.unitPriceCents())
                .build();
        invoice.getItems().add(item);
        invoiceRepository.save(invoice);
        return assembler.toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse issue(UUID invoiceId, UUID actorUserId, String actorEmail) {
        Invoice invoice = requireInvoice(invoiceId);
        InvoicePolicy.requireIssuable(invoice.getStatus());

        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());
        invoiceRepository.save(invoice);

        auditService.recordEvent(AuditEventType.INVOICE_ISSUED, actorUserId, actorEmail);
        return assembler.toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID invoiceId, UUID actorUserId, String actorEmail) {
        Invoice invoice = requireInvoice(invoiceId);
        InvoicePolicy.requireVoidable(invoice.getStatus());

        invoice.setStatus(InvoiceStatus.VOID);
        invoiceRepository.save(invoice);

        auditService.recordEvent(AuditEventType.INVOICE_VOIDED, actorUserId, actorEmail);
        return assembler.toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID invoiceId) {
        return assembler.toResponse(requireInvoice(invoiceId));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> listForPatient(UUID patientProfileId, Pageable pageable) {
        return invoiceRepository
                .findByPatientProfileIdOrderByCreatedAtDesc(patientProfileId, pageable)
                .map(assembler::toResponse);
    }

    Invoice requireInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }
}
