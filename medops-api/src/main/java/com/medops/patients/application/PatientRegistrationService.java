package com.medops.patients.application;

import java.security.SecureRandom;
import java.time.Year;
import java.time.ZoneOffset;
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
import com.medops.patients.api.dto.RegisterPatientRequest;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ConflictException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PatientRegistrationService {

    private static final String PATIENT_ROLE = "PATIENT";
    private static final int MAX_MRN_ATTEMPTS = 5;
    private static final int MRN_SEQUENCE_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuanceService tokenIssuanceService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthResponse registerPatient(RegisterPatientRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }

        Role patientRole = roleRepository.findByName(PATIENT_ROLE)
                .orElseThrow(() -> new IllegalStateException(PATIENT_ROLE + " role is not bootstrapped"));

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(new HashSet<>(Set.of(patientRole)))
                .build();
        user = userRepository.save(Objects.requireNonNull(user));

        PatientProfile profile = PatientProfile.builder()
                .userId(user.getId())
                .mrn(generateUniqueMrn())
                .fullName(request.fullName())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .phoneNumber(request.phoneNumber())
                .build();
        patientProfileRepository.save(Objects.requireNonNull(profile));

        auditService.recordEvent(AuditEventType.AUTH_REGISTER, user.getId(), user.getEmail());

        return tokenIssuanceService.issue(user);
    }

    private String generateUniqueMrn() {
        for (int attempt = 0; attempt < MAX_MRN_ATTEMPTS; attempt++) {
            String candidate = "MRN-" + Year.now(ZoneOffset.UTC) + "-"
                    + String.format("%06d", secureRandom.nextInt(MRN_SEQUENCE_BOUND));
            if (!patientProfileRepository.existsByMrn(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique MRN after " + MAX_MRN_ATTEMPTS + " attempts");
    }
}
