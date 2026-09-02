package com.medops.billing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medops.billing.api.dto.InvoiceResponse;
import com.medops.billing.api.dto.PaymentResponse;
import com.medops.billing.application.InvoiceService;
import com.medops.billing.application.PaymentService;
import com.medops.clinical.ClinicalAccessService;
import com.medops.shared.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/billing")
@RequiredArgsConstructor
public class BillingController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final ClinicalAccessService clinicalAccess;

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> listInvoices(
            Authentication auth,
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable) {
        clinicalAccess.assertPatientOwns(patientId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(invoiceService.listForPatient(patientId, pageable)));
    }

    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(
            Authentication auth,
            @PathVariable UUID patientId,
            @PathVariable UUID invoiceId) {
        clinicalAccess.assertPatientOwns(patientId, auth.getName());
        InvoiceResponse invoice = invoiceService.get(invoiceId);
        return ResponseEntity.ok(ApiResponse.success(invoice));
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listPayments(
            Authentication auth,
            @PathVariable UUID patientId,
            @PathVariable UUID invoiceId) {
        clinicalAccess.assertPatientOwns(patientId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(paymentService.listForInvoice(invoiceId)));
    }
}
