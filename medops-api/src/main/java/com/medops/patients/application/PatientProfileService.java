package com.medops.patients.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.auth.entity.User;
import com.medops.auth.repository.UserRepository;
import com.medops.patients.api.dto.PatientProfileResponse;
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
}
