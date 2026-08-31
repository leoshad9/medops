package com.medops.doctors.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.auth.entity.User;
import com.medops.auth.repository.UserRepository;
import com.medops.cache.domain.DoctorDirectoryCache;
import com.medops.doctors.api.dto.DoctorProfileResponse;
import com.medops.doctors.api.dto.DoctorSummaryResponse;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorProfileService {

    private final UserRepository userRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final DoctorDirectoryCache doctorDirectoryCache;

    @Transactional(readOnly = true)
    public DoctorProfileResponse getMyProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        DoctorProfile profile = doctorProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No doctor profile for user: " + email));

        return DoctorProfileResponse.of(profile, user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<DoctorSummaryResponse> listDoctors(String specialty) {
        String cacheKey = specialty == null || specialty.isBlank() ? "all" : specialty.trim().toLowerCase();
        return doctorDirectoryCache.get(cacheKey).orElseGet(() -> {
            List<DoctorProfile> profiles = specialty == null || specialty.isBlank()
                    ? doctorProfileRepository.findAllByOrderByFullNameAsc()
                    : doctorProfileRepository.findBySpecialtyIgnoreCaseOrderByFullNameAsc(specialty.trim());
            List<DoctorSummaryResponse> response = profiles.stream().map(DoctorSummaryResponse::from).toList();
            doctorDirectoryCache.put(cacheKey, response);
            return response;
        });
    }
}
