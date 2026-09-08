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

import com.medops.auth.entity.User;
import com.medops.auth.repository.UserRepository;
import com.medops.patients.api.dto.PatientProfileResponse;
import com.medops.patients.api.dto.UpdatePatientProfileRequest;
import com.medops.patients.domain.Gender;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PatientProfileService}. Pure Mockito - no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class PatientProfileServiceTest {

    private static final String EMAIL = "patient@medops.dev";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;

    @InjectMocks
    private PatientProfileService service;

    @Test
    void getMyProfile_mapsProfileAndEmailFromLoggedInUser() {
        UUID userId = UUID.randomUUID();
        PatientProfile profile = PatientProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .mrn("MRN-2026-000001")
                .fullName("Jane Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(Gender.FEMALE)
                .phoneNumber("+12345678901")
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(userId)));
        when(patientProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        PatientProfileResponse response = service.getMyProfile(EMAIL);

        assertThat(response.fullName()).isEqualTo("Jane Doe");
        assertThat(response.mrn()).isEqualTo("MRN-2026-000001");
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.bloodGroup()).isNull();
    }

    @Test
    void updateMyProfile_persistsEditableFieldsAndNormalizesBlanks() {
        UUID userId = UUID.randomUUID();
        PatientProfile profile = PatientProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .mrn("MRN-2026-000001")
                .fullName("Old Name")
                .phoneNumber("+12345678901")
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(userId)));
        when(patientProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(patientProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest(
                "Jane Doe", "+10987654321", "O+", " 24 Main Road  ",
                "Anita (Spouse) +10987654322", "Star Health", "SH-88213");

        PatientProfileResponse response = service.updateMyProfile(EMAIL, request);

        ArgumentCaptor<PatientProfile> captor = ArgumentCaptor.forClass(PatientProfile.class);
        verify(patientProfileRepository).save(captor.capture());
        PatientProfile saved = captor.getValue();
        assertThat(saved.getFullName()).isEqualTo("Jane Doe");
        assertThat(saved.getPhoneNumber()).isEqualTo("+10987654321");
        assertThat(saved.getBloodGroup()).isEqualTo("O+");
        assertThat(saved.getAddress()).isEqualTo("24 Main Road");
        assertThat(saved.getInsuranceProvider()).isEqualTo("Star Health");
        assertThat(response.fullName()).isEqualTo("Jane Doe");
        assertThat(response.bloodGroup()).isEqualTo("O+");
    }

    @Test
    void updateMyProfile_storesBlankOptionalsAsNull() {
        UUID userId = UUID.randomUUID();
        PatientProfile profile = PatientProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .mrn("MRN-2026-000002")
                .fullName("Jane Doe")
                .phoneNumber("+12345678901")
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(userId)));
        when(patientProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(patientProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePatientProfileRequest request = new UpdatePatientProfileRequest(
                "Jane Doe", "+12345678901", "", "   ", null, null, "");

        service.updateMyProfile(EMAIL, request);

        ArgumentCaptor<PatientProfile> captor = ArgumentCaptor.forClass(PatientProfile.class);
        verify(patientProfileRepository).save(captor.capture());
        PatientProfile saved = captor.getValue();
        assertThat(saved.getBloodGroup()).isNull();
        assertThat(saved.getAddress()).isNull();
        assertThat(saved.getEmergencyContact()).isNull();
        assertThat(saved.getInsurancePolicyNumber()).isNull();
    }

    private static User user(UUID id) {
        return User.builder().id(id).email(EMAIL).build();
    }
}