package com.medops.prescriptions.api;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medops.prescriptions.api.dto.CreatePrescriptionRequest;
import com.medops.prescriptions.api.dto.PrescriptionResponse;
import com.medops.prescriptions.application.CreatePrescriptionService;
import com.medops.prescriptions.application.PrescriptionQueryService;
import com.medops.shared.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PrescriptionController {

    private final CreatePrescriptionService createPrescriptionService;
    private final PrescriptionQueryService prescriptionQueryService;

    @PostMapping("/api/v1/patients/{patientId}/prescriptions")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> create(
            Authentication authentication,
            @PathVariable UUID patientId,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        PrescriptionResponse response = createPrescriptionService.create(
                authentication.getName(), patientId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Prescription created"));
    }

    @GetMapping("/api/v1/prescriptions")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> list(
            Authentication authentication,
            @RequestParam(required = false) UUID patientId) {
        List<PrescriptionResponse> items = prescriptionQueryService.list(authentication.getName(), patientId);
        return ResponseEntity.ok(ApiResponse.success(items));
    }
}
