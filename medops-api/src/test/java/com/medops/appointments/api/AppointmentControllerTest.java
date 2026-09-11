package com.medops.appointments.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.api.dto.BookAppointmentRequest;
import com.medops.appointments.application.AppointmentQueryService;
import com.medops.appointments.application.AppointmentTransitionService;
import com.medops.appointments.application.BookAppointmentService;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.auth.security.AuthRateLimitFilter;
import com.medops.auth.security.JwtAuthenticationFilter;
import com.medops.idempotency.application.IdempotencyExecutor;
import com.medops.shared.exception.ConflictException;

@WebMvcTest(
        controllers = AppointmentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc
class AppointmentControllerTest {

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookAppointmentService bookAppointmentService;
    @MockitoBean
    private AppointmentQueryService appointmentQueryService;
    @MockitoBean
    private AppointmentTransitionService appointmentTransitionService;
    @MockitoBean
    private IdempotencyExecutor idempotencyExecutor;

    @BeforeEach
    void passthroughIdempotency() {
        when(idempotencyExecutor.execute(any(), any(), any(), any(), eq(AppointmentResponse.class), any()))
                .thenAnswer(invocation -> {
                    Supplier<AppointmentResponse> action = invocation.getArgument(5);
                    return action.get();
                });
    }

    @Test
    @WithMockUser(username = "patient@medops.dev", roles = "PATIENT")
    void bookReturns201WithEnvelopeOnSuccess() throws Exception {
        UUID doctorId = UUID.randomUUID();
        Instant start = Instant.parse("2026-08-31T04:30:00Z");
        AppointmentResponse response = new AppointmentResponse(
                UUID.randomUUID(), UUID.randomUUID(), doctorId,
                "Test Patient", "MRN-1", "Dr. Khan", "Cardiology",
                start, start.plusSeconds(1800), AppointmentStatus.BOOKED, "review", null);
        when(bookAppointmentService.book(eq("patient@medops.dev"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .header("Idempotency-Key", "book-1")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(new BookAppointmentRequest(doctorId, start, "review"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("BOOKED"));
    }

    @Test
    @WithMockUser(username = "patient@medops.dev", roles = "PATIENT")
    void bookReturns400WhenDoctorMissing() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(JSON)
                        .content("{\"startsAt\":\"2026-08-31T04:30:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.status").value("INVALID_ARGUMENT"));
    }

    @Test
    @WithMockUser(username = "patient@medops.dev", roles = "PATIENT")
    void bookReturns409WhenSlotTaken() throws Exception {
        UUID doctorId = UUID.randomUUID();
        Instant start = Instant.parse("2026-08-31T04:30:00Z");
        when(bookAppointmentService.book(eq("patient@medops.dev"), any()))
                .thenThrow(new ConflictException("That time is no longer available"));

        mockMvc.perform(post("/api/v1/appointments")
                        .with(csrf())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(new BookAppointmentRequest(doctorId, start, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.status").value("ALREADY_EXISTS"));
    }

}
