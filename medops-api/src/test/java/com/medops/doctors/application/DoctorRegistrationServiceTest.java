package com.medops.doctors.application;

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
import com.medops.cache.domain.DoctorDirectoryCache;
import com.medops.doctors.api.dto.RegisterDoctorRequest;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ConflictException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DoctorRegistrationService}. Pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class DoctorRegistrationServiceTest {

    private static final String EMAIL = "doctor@medops.dev";
    private static final String PASSWORD = "Password123!";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final String LICENSE_NUMBER = "LIC-000123";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenIssuanceService tokenIssuanceService;
    @Mock
    private AuditService auditService;
    @Mock
    private DoctorDirectoryCache doctorDirectoryCache;

    @InjectMocks
    private DoctorRegistrationService service;

    @Test
    void registerDoctorCreatesUserAndProfileAndReturnsTokens() {
        RegisterDoctorRequest request = new RegisterDoctorRequest(
                EMAIL, PASSWORD, "Dr. Sarah Khan", "Cardiology", LICENSE_NUMBER, "+12345678901");
        Role doctorRole = Role.builder().id(UUID.randomUUID()).name("DOCTOR").build();
        AuthResponse expectedTokens = AuthResponse.of("access", "refresh", 900_000L);

        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(doctorProfileRepository.existsByLicenseNumber(LICENSE_NUMBER)).thenReturn(false);
        when(roleRepository.findByName("DOCTOR")).thenReturn(Optional.of(doctorRole));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenIssuanceService.issue(any(User.class))).thenReturn(expectedTokens);

        AuthResponse response = service.registerDoctor(request);

        assertThat(response).isEqualTo(expectedTokens);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(userCaptor.getValue().getRoles()).containsExactly(doctorRole);

        ArgumentCaptor<DoctorProfile> profileCaptor = ArgumentCaptor.forClass(DoctorProfile.class);
        verify(doctorProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getFullName()).isEqualTo("Dr. Sarah Khan");
        assertThat(profileCaptor.getValue().getSpecialty()).isEqualTo("Cardiology");
        assertThat(profileCaptor.getValue().getLicenseNumber()).isEqualTo(LICENSE_NUMBER);

        verify(doctorDirectoryCache).evictAll();
        verify(auditService).recordEvent(eq(AuditEventType.AUTH_REGISTER), any(), eq(EMAIL));
    }

    @Test
    void registerDoctorThrowsConflictWhenEmailAlreadyExists() {
        RegisterDoctorRequest request = new RegisterDoctorRequest(
                EMAIL, PASSWORD, "Dr. Sarah Khan", "Cardiology", LICENSE_NUMBER, "+12345678901");
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.registerDoctor(request));

        verify(userRepository, never()).save(any());
        verify(doctorProfileRepository, never()).save(any());
    }

    @Test
    void registerDoctorThrowsConflictWhenLicenseNumberAlreadyExists() {
        RegisterDoctorRequest request = new RegisterDoctorRequest(
                EMAIL, PASSWORD, "Dr. Sarah Khan", "Cardiology", LICENSE_NUMBER, "+12345678901");
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(doctorProfileRepository.existsByLicenseNumber(LICENSE_NUMBER)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.registerDoctor(request));

        verify(userRepository, never()).save(any());
        verify(doctorProfileRepository, never()).save(any());
    }
}
