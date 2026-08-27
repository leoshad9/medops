package com.medops.doctors.application;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.auth.dto.AuthResponse;
import com.medops.auth.entity.Role;
import com.medops.auth.entity.User;
import com.medops.auth.repository.RoleRepository;
import com.medops.auth.repository.UserRepository;
import com.medops.auth.service.TokenIssuanceService;
import com.medops.doctors.api.dto.RegisterDoctorRequest;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ConflictException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorRegistrationService {

    private static final String DOCTOR_ROLE = "DOCTOR";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuanceService tokenIssuanceService;
    private final AuditService auditService;

    @Transactional
    public AuthResponse registerDoctor(RegisterDoctorRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }
        if (doctorProfileRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new ConflictException("An account with this license number already exists");
        }

        Role doctorRole = roleRepository.findByName(DOCTOR_ROLE)
                .orElseThrow(() -> new IllegalStateException(DOCTOR_ROLE + " role is not bootstrapped"));

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(new HashSet<>(Set.of(doctorRole)))
                .build();
        user = userRepository.save(Objects.requireNonNull(user));

        DoctorProfile profile = DoctorProfile.builder()
                .userId(user.getId())
                .fullName(request.fullName())
                .specialty(request.specialty())
                .licenseNumber(request.licenseNumber())
                .phoneNumber(request.phoneNumber())
                .build();
        doctorProfileRepository.save(Objects.requireNonNull(profile));

        auditService.recordEvent(AuditEventType.AUTH_REGISTER, user.getId(), user.getEmail());

        return tokenIssuanceService.issue(user);
    }
}
