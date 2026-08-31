package com.medops.doctors.api;

import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.dto.AuthResponse;
import com.medops.auth.security.AuthRateLimitFilter;
import com.medops.auth.security.JwtAuthenticationFilter;
import com.medops.doctors.api.dto.RegisterDoctorRequest;
import com.medops.doctors.application.DoctorPatientRosterService;
import com.medops.doctors.application.DoctorProfileService;
import com.medops.doctors.application.DoctorRegistrationService;
import com.medops.shared.exception.ConflictException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for {@link DoctorController#register}: request validation, the
 * {@code ApiResponse} envelope, and error mapping through
 * {@link com.medops.shared.exception.GlobalExceptionHandler}. Security filters are disabled
 * here (see {@code addFilters = false}) since registration is intentionally public - the same
 * pattern used by {@code AuthControllerTest}.
 */
@WebMvcTest(
        controllers = DoctorController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class DoctorControllerTest {

    private static final AuthResponse SAMPLE_RESPONSE =
            new AuthResponse("access-token", "refresh-token", "Bearer", 900L);

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DoctorRegistrationService doctorRegistrationService;

    @MockitoBean
    private DoctorProfileService doctorProfileService;

    @MockitoBean
    private DoctorPatientRosterService doctorPatientRosterService;

    private @NonNull String json(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value));
    }

    private static RegisterDoctorRequest validRequest() {
        return new RegisterDoctorRequest(
                "doctor@medops.dev", "Password123!", "Dr. Sarah Khan",
                "Cardiology", "LIC-000123", "+12345678901");
    }

    @Test
    void registerReturns201WithEnvelopeOnSuccess() throws Exception {
        when(doctorRegistrationService.registerDoctor(any())).thenReturn(SAMPLE_RESPONSE);

        mockMvc.perform(post("/api/v1/doctors")
                        .contentType(JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void registerReturns400OnBlankLicenseNumber() throws Exception {
        RegisterDoctorRequest invalid = new RegisterDoctorRequest(
                "doctor@medops.dev", "Password123!", "Dr. Sarah Khan", "Cardiology", " ", "+12345678901");

        mockMvc.perform(post("/api/v1/doctors")
                        .contentType(JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.status").value("INVALID_ARGUMENT"));
    }

    @Test
    void registerReturns409OnDuplicateLicenseNumber() throws Exception {
        when(doctorRegistrationService.registerDoctor(any()))
                .thenThrow(new ConflictException("An account with this license number already exists"));

        mockMvc.perform(post("/api/v1/doctors")
                        .contentType(JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.status").value("ALREADY_EXISTS"));
    }
}
