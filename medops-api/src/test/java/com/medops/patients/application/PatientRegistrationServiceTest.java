package com.medops.patients.application;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.medops.auth.dto.AuthResponse;
import com.medops.auth.entity.Role;
import com.medops.auth.entity.User;
import com.medops.auth.repository.RoleRepository;
import com.medops.auth.repository.UserRepository;
import com.medops.auth.service.TokenIssuanceService;
import com.medops.patients.api.dto.RegisterPatientRequest;
import com.medops.patients.domain.Gender;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ConflictException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PatientRegistrationService}. Pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class PatientRegistrationServiceTest {

    private static final String EMAIL = "patient@medops.dev";
    private static final String PASSWORD = "Password123!";
    private static final String ENCODED_PASSWORD = "encoded-password";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenIssuanceService tokenIssuanceService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PatientRegistrationService service;

    @Test
    void registerPatient_createsUserAndProfile_andReturnsTokens() {
        RegisterPatientRequest request = new RegisterPatientRequest(
                EMAIL, PASSWORD, "Jane Doe", LocalDate.of(1990, 1, 1), Gender.FEMALE, "+12345678901");
        Role patientRole = Role.builder().id(UUID.randomUUID()).name("PATIENT").build();
        AuthResponse expectedTokens = AuthResponse.of("access", "refresh", 900_000L);

        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(roleRepository.findByName("PATIENT")).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientProfileRepository.existsByMrn(anyString())).thenReturn(false);
        when(tokenIssuanceService.issue(any(User.class))).thenReturn(expectedTokens);

        AuthResponse response = service.registerPatient(request);

        assertThat(response).isEqualTo(expectedTokens);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
        assertThat(userCaptor.getValue().getRoles()).containsExactly(patientRole);

        ArgumentCaptor<PatientProfile> profileCaptor = ArgumentCaptor.forClass(PatientProfile.class);
        verify(patientProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getFullName()).isEqualTo("Jane Doe");
        assertThat(profileCaptor.getValue().getMrn()).startsWith("MRN-");

        verify(auditService).recordEvent(eq(AuditEventType.AUTH_REGISTER), any(), eq(EMAIL));
    }

    @Test
    void registerPatient_throwsConflict_whenEmailAlreadyExists() {
        RegisterPatientRequest request = new RegisterPatientRequest(
                EMAIL, PASSWORD, "Jane Doe", LocalDate.of(1990, 1, 1), Gender.FEMALE, "+12345678901");
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.registerPatient(request));

        verify(userRepository, never()).save(any());
        verify(patientProfileRepository, never()).save(any());
    }
}
