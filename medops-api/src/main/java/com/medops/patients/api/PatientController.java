package com.medops.patients.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medops.auth.dto.AuthResponse;
import com.medops.patients.api.dto.PatientProfileResponse;
import com.medops.patients.api.dto.RegisterPatientRequest;
import com.medops.patients.application.PatientProfileService;
import com.medops.patients.application.PatientRegistrationService;
import com.medops.shared.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientRegistrationService patientRegistrationService;
    private final PatientProfileService patientProfileService;

    @PostMapping
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterPatientRequest request) {
        AuthResponse response = patientRegistrationService.registerPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Patient account created"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientProfileResponse>> getMyProfile(Authentication authentication) {
        PatientProfileResponse response = patientProfileService.getMyProfile(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
