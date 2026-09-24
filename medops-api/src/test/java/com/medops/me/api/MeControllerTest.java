package com.medops.me.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medops.auth.security.filters.AuthRateLimitFilter;
import com.medops.auth.security.jwt.JwtAuthenticationFilter;
import com.medops.auth.security.principal.MedOpsUser;
import com.medops.doctors.api.dto.DoctorProfileResponse;
import com.medops.me.api.dto.MeResponse;
import com.medops.me.application.MeBootstrapService;
import com.medops.patients.api.dto.PatientProfileResponse;
import com.medops.patients.domain.Gender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for {@link MeController}: the single-request session bootstrap must
 * return the identity plus the role-specific profile in the standard {@code ApiResponse}
 * envelope, and must stay closed to unauthenticated callers. Requests authenticate with
 * a real {@link MedOpsUser} because {@code @AuthenticationPrincipal} only resolves that
 * principal type.
 */
@WebMvcTest(
        controllers = MeController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc
class MeControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String EMAIL = "patient@medops.dev";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeBootstrapService meBootstrapService;

    private static MedOpsUser principal(String email, String role) {
        return new MedOpsUser(USER_ID, email, "hash", role,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), true, true);
    }

    @Test
    void me_returns200WithPatientProfile_forPatientSession() throws Exception {
        when(meBootstrapService.load(any())).thenReturn(new MeResponse(
                USER_ID, EMAIL, "PATIENT",
                new PatientProfileResponse(UUID.randomUUID(), EMAIL, "Jane Doe", "MRN-2026-000001",
                        LocalDate.of(1990, 1, 1), Gender.FEMALE, "+12345678901",
                        "O+", "24 Main Road", "Anita (Spouse) +12345678902", "Star Health", "SH-88213"),
                null));

        mockMvc.perform(get("/api/v1/me").with(user(principal(EMAIL, "PATIENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Session active"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("PATIENT"))
                .andExpect(jsonPath("$.data.patientProfile.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.patientProfile.mrn").value("MRN-2026-000001"));
    }

    @Test
    void me_returns200WithDoctorProfile_forDoctorSession() throws Exception {
        String doctorEmail = "doctor@medops.dev";
        when(meBootstrapService.load(any())).thenReturn(new MeResponse(
                USER_ID, doctorEmail, "DOCTOR", null,
                new DoctorProfileResponse(doctorEmail, "Dr. Ada Lovelace",
                        "Cardiology", "LIC-12345", "+10987654321")));

        mockMvc.perform(get("/api/v1/me").with(user(principal(doctorEmail, "DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("DOCTOR"))
                .andExpect(jsonPath("$.data.doctorProfile.fullName").value("Dr. Ada Lovelace"))
                .andExpect(jsonPath("$.data.doctorProfile.specialty").value("Cardiology"));
    }

    @Test
    void me_returns401_forAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }
}
