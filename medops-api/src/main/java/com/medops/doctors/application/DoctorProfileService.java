package com.medops.doctors.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.auth.entity.User;
import com.medops.auth.repository.UserRepository;
import com.medops.doctors.api.dto.DoctorProfileResponse;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorProfileService {

    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    @Transactional(readOnly = true)
    public DoctorProfileResponse getMyProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        DoctorProfile profile = doctorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No doctor profile for user: " + email));

        return DoctorProfileResponse.of(profile, user.getEmail());
    }
}
