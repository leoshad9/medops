package com.medops.patients.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.auth.entity.User;
import com.medops.auth.repository.UserRepository;
import com.medops.patients.api.dto.PatientProfileResponse;
import com.medops.patients.api.dto.UpdatePatientProfileRequest;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PatientProfileService {

    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;

    @Transactional(readOnly = true)
    public PatientProfileResponse getMyProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        PatientProfile profile = patientProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No patient profile for user: " + email));

        return PatientProfileResponse.of(profile, user.getEmail());
    }

    @Transactional
    public PatientProfileResponse updateMyProfile(String email, UpdatePatientProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        PatientProfile profile = patientProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No patient profile for user: " + email));

        profile.setFullName(request.fullName());
        profile.setPhoneNumber(request.phoneNumber());
        profile.setBloodGroup(blankToNull(request.bloodGroup()));
        profile.setAddress(blankToNull(request.address()));
        profile.setEmergencyContact(blankToNull(request.emergencyContact()));
        profile.setInsuranceProvider(blankToNull(request.insuranceProvider()));
        profile.setInsurancePolicyNumber(blankToNull(request.insurancePolicyNumber()));

        return PatientProfileResponse.of(patientProfileRepository.save(profile), user.getEmail());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
