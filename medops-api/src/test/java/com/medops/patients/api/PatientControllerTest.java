package com.medops.patients.api;

import java.time.LocalDate;
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
import com.medops.patients.api.dto.RegisterPatientRequest;
import com.medops.patients.application.PatientProfileService;
import com.medops.patients.application.PatientRegistrationService;
import com.medops.patients.domain.Gender;
import com.medops.shared.exception.ConflictException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for {@link PatientController#register}: request validation, the
 * {@code ApiResponse} envelope, and error mapping through
 * {@link com.medops.shared.exception.GlobalExceptionHandler}. Security filters are disabled
 * here (see {@code addFilters = false}) since registration is intentionally public - the same
 * pattern used by {@code AuthControllerTest}.
 */
@WebMvcTest(
        controllers = PatientController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class PatientControllerTest {

    private static final AuthResponse SAMPLE_RESPONSE =
            new AuthResponse("access-token", "refresh-token", "Bearer", 900L);

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PatientRegistrationService patientRegistrationService;

    @MockitoBean
    private PatientProfileService patientProfileService;

    private @NonNull String json(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value));
    }

    private static RegisterPatientRequest validRequest() {
        return new RegisterPatientRequest(
                "patient@medops.dev", "Password123!", "Jane Doe",
                LocalDate.of(1990, 1, 1), Gender.FEMALE, "+12345678901");
    }

    @Test
    void register_returns201WithEnvelope_onSuccess() throws Exception {
        when(patientRegistrationService.registerPatient(any())).thenReturn(SAMPLE_RESPONSE);

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void register_returns400_onInvalidEmail() throws Exception {
        RegisterPatientRequest invalid = new RegisterPatientRequest(
                "not-an-email", "Password123!", "Jane Doe",
                LocalDate.of(1990, 1, 1), Gender.FEMALE, "+12345678901");

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.status").value("INVALID_ARGUMENT"));
    }

    @Test
    void register_returns409_onDuplicateEmail() throws Exception {
        when(patientRegistrationService.registerPatient(any()))
                .thenThrow(new ConflictException("An account with this email already exists"));

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.status").value("ALREADY_EXISTS"));
    }
}
