package com.medops.doctors.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medops.auth.security.filters.AuthRateLimitFilter;
import com.medops.auth.security.jwt.JwtAuthenticationFilter;
import com.medops.doctors.api.dto.DoctorDashboardResponse;
import com.medops.doctors.application.DoctorDashboardService;
import com.medops.doctors.application.DoctorPatientRosterService;
import com.medops.doctors.application.DoctorProfileService;
import com.medops.doctors.application.DoctorRegistrationService;

/**
 * HTTP-level tests for {@code GET /api/v1/doctors/me/dashboard}. Unlike
 * {@link DoctorControllerTest}, security filters stay enabled here so the
 * {@code Authentication} controller argument is resolved from the
 * {@code @WithMockUser} session - the same pattern as
 * {@code NotificationControllerTest}.
 */
@WebMvcTest(
        controllers = DoctorController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc
class DoctorDashboardControllerTest {

    private static final String DOCTOR_EMAIL = "doctor@medops.dev";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DoctorRegistrationService doctorRegistrationService;

    @MockitoBean
    private DoctorProfileService doctorProfileService;

    @MockitoBean
    private DoctorPatientRosterService doctorPatientRosterService;

    @MockitoBean
    private DoctorDashboardService doctorDashboardService;

    @Test
    @WithMockUser(username = DOCTOR_EMAIL, roles = "DOCTOR")
    void dashboardReturnsAggregatedEnvelope() throws Exception {
        DoctorDashboardResponse response = new DoctorDashboardResponse(
                3, 1, 2, 5, List.of(), List.of(), List.of(), Instant.parse("2026-09-20T05:00:00Z"));
        when(doctorDashboardService.getMyDashboard(DOCTOR_EMAIL)).thenReturn(response);

        mockMvc.perform(get("/api/v1/doctors/me/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.todayScheduleCount").value(3))
                .andExpect(jsonPath("$.data.completedTodayCount").value(1))
                .andExpect(jsonPath("$.data.awaitingTodayCount").value(2))
                .andExpect(jsonPath("$.data.pendingLabReportsCount").value(5))
                .andExpect(jsonPath("$.data.upcomingAppointments").isArray())
                .andExpect(jsonPath("$.data.pendingLabReports").isArray());
    }

    @Test
    @WithMockUser(username = DOCTOR_EMAIL, roles = "DOCTOR")
    void dashboardPropagatesAccessDeniedAsErrorEnvelope() throws Exception {
        when(doctorDashboardService.getMyDashboard(DOCTOR_EMAIL))
                .thenThrow(new AccessDeniedException("A doctor profile is required"));

        mockMvc.perform(get("/api/v1/doctors/me/dashboard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.status").value("PERMISSION_DENIED"));
    }
}