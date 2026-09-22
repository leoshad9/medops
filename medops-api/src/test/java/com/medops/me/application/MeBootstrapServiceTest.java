package com.medops.me.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.medops.auth.security.principal.MedOpsUser;
import com.medops.doctors.api.dto.DoctorProfileResponse;
import com.medops.doctors.application.DoctorProfileService;
import com.medops.me.api.dto.MeResponse;
import com.medops.patients.api.dto.PatientProfileResponse;
import com.medops.patients.application.PatientProfileService;
import com.medops.patients.domain.Gender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeBootstrapServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String EMAIL = "patient@medops.dev";

    private static final PatientProfileResponse PATIENT_PROFILE = new PatientProfileResponse(
            UUID.randomUUID(), EMAIL, "Jane Doe", "MRN-2026-000001", LocalDate.of(1990, 1, 1),
            Gender.FEMALE, "+12345678901", "O+", "24 Main Road",
            "Anita (Spouse) +12345678902", "Star Health", "SH-88213");

    private static final DoctorProfileResponse DOCTOR_PROFILE = new DoctorProfileResponse(
            "doctor@medops.dev", "Dr. Ada Lovelace", "Cardiology", "LIC-12345", "+10987654321");

    @Mock
    private PatientProfileService patientProfileService;

    @Mock
    private DoctorProfileService doctorProfileService;

    @InjectMocks
    private MeBootstrapService service;

    private static MedOpsUser principal(String email, String role) {
        return new MedOpsUser(USER_ID, email, "hash", role,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), true, true);
    }

    @Test
    void load_returnsIdentityWithPatientProfile_forPatient() {
        when(patientProfileService.getMyProfile(EMAIL)).thenReturn(PATIENT_PROFILE);

        MeResponse response = service.load(principal(EMAIL, "PATIENT"));

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.role()).isEqualTo("PATIENT");
        assertThat(response.patientProfile()).isEqualTo(PATIENT_PROFILE);
        assertThat(response.doctorProfile()).isNull();
        verifyNoInteractions(doctorProfileService);
    }

    @Test
    void load_returnsIdentityWithDoctorProfile_forDoctor() {
        when(doctorProfileService.getMyProfile(EMAIL)).thenReturn(DOCTOR_PROFILE);

        MeResponse response = service.load(principal(EMAIL, "DOCTOR"));

        assertThat(response.role()).isEqualTo("DOCTOR");
        assertThat(response.doctorProfile()).isEqualTo(DOCTOR_PROFILE);
        assertThat(response.patientProfile()).isNull();
        verifyNoInteractions(patientProfileService);
    }

    @Test
    void load_keepsIdentity_whenProfileLookupFails() {
        when(patientProfileService.getMyProfile(EMAIL))
                .thenThrow(new IllegalStateException("Authenticated user not found: " + EMAIL));

        MeResponse response = service.load(principal(EMAIL, "PATIENT"));

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.role()).isEqualTo("PATIENT");
        assertThat(response.patientProfile()).isNull();
    }
}
