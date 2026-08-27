package com.medops.doctors.api;

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
import com.medops.doctors.api.dto.DoctorProfileResponse;
import com.medops.doctors.api.dto.RegisterDoctorRequest;
import com.medops.doctors.application.DoctorProfileService;
import com.medops.doctors.application.DoctorRegistrationService;
import com.medops.shared.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorRegistrationService doctorRegistrationService;
    private final DoctorProfileService doctorProfileService;

    @PostMapping
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterDoctorRequest request) {
        AuthResponse response = doctorRegistrationService.registerDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Doctor account created"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<DoctorProfileResponse>> getMyProfile(Authentication authentication) {
        DoctorProfileResponse response = doctorProfileService.getMyProfile(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
