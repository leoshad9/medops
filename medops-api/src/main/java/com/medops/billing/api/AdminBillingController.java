package com.medops.billing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medops.billing.api.dto.AddInvoiceItemRequest;
import com.medops.billing.api.dto.CreateInvoiceRequest;
import com.medops.billing.api.dto.InvoiceResponse;
import com.medops.billing.api.dto.PaymentResponse;
import com.medops.billing.api.dto.RecordPaymentRequest;
import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.billing.application.InvoiceService;
import com.medops.billing.application.PaymentService;
import com.medops.shared.exception.InvalidRequestException;
import com.medops.shared.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/v1/admin/billing")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class AdminBillingController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final AppointmentActorResolver actorResolver;

    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<InvoiceResponse>> create(
            Authentication auth,
            @Valid @RequestBody CreateInvoiceRequest request) {
        UUID actorId = resolveUserId(auth);
        InvoiceResponse response = invoiceService.create(request, actorId, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Invoice created"));
    }

    @PostMapping("/invoices/{invoiceId}/items")
    public ResponseEntity<ApiResponse<InvoiceResponse>> addItem(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody AddInvoiceItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(invoiceService.addItem(invoiceId, request), "Item added"));
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    public ResponseEntity<ApiResponse<InvoiceResponse>> issue(
            Authentication auth,
            @PathVariable UUID invoiceId) {
        UUID actorId = resolveUserId(auth);
        return ResponseEntity.ok(ApiResponse.success(invoiceService.issue(invoiceId, actorId, auth.getName()), "Invoice issued"));
    }

    @PostMapping("/invoices/{invoiceId}/void")
    public ResponseEntity<ApiResponse<InvoiceResponse>> voidInvoice(
            Authentication auth,
            @PathVariable UUID invoiceId) {
        UUID actorId = resolveUserId(auth);
        return ResponseEntity.ok(ApiResponse.success(invoiceService.voidInvoice(invoiceId, actorId, auth.getName()), "Invoice voided"));
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.get(invoiceId)));
    }

    @PostMapping("/invoices/{invoiceId}/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordPayment(
            Authentication auth,
            @PathVariable UUID invoiceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RecordPaymentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key header is required");
        }
        UUID actorId = resolveUserId(auth);
        PaymentResponse response = paymentService.recordPayment(
                invoiceId, idempotencyKey, request, actorId, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Payment recorded"));
    }

    @PostMapping("/payments/{paymentId}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            Authentication auth,
            @PathVariable UUID paymentId) {
        UUID actorId = resolveUserId(auth);
        return ResponseEntity.ok(ApiResponse.success(paymentService.refund(paymentId, actorId, auth.getName()), "Payment refunded"));
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listPayments(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.listForInvoice(invoiceId)));
    }

    private UUID resolveUserId(Authentication auth) {
        return actorResolver.requireActiveUser(auth.getName()).getId();
    }
}
