package com.medops.notification.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.auth.entity.User;
import com.medops.auth.security.AuthRateLimitFilter;
import com.medops.auth.security.JwtAuthenticationFilter;
import com.medops.notification.api.dto.NotificationPageResponse;
import com.medops.notification.api.dto.NotificationResponse;
import com.medops.notification.api.dto.UnreadCountResponse;
import com.medops.notification.application.NotificationQueryService;
import com.medops.notification.application.NotificationService;
import com.medops.notification.domain.NotificationType;
import com.medops.notification.infrastructure.sse.NotificationStreamPublisher;

@WebMvcTest(
        controllers = {NotificationController.class, NotificationStreamController.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    private static final @NonNull String PATIENT_EMAIL = "patient@medops.dev";
    private static final @NonNull MediaType JSON = java.util.Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationQueryService notificationQueryService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private NotificationStreamPublisher streamPublisher;
    @MockitoBean
    private AppointmentActorResolver actorResolver;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubAuthenticatedUser() {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());
    }

    @Test
    void listReturnsPagedNotifications() throws Exception {
        NotificationResponse notification = new NotificationResponse(
                UUID.randomUUID(), NotificationType.APPOINTMENT_BOOKED, "Appointment confirmed",
                "Your appointment is confirmed.", "APPOINTMENT", UUID.randomUUID(), false,
                Instant.parse("2026-08-31T04:30:00Z"));
        NotificationPageResponse page = new NotificationPageResponse(List.of(notification), 0, 20, 1);
        when(notificationQueryService.list(eq(userId), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications").principal(patientAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].title").value("Appointment confirmed"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void unreadCountReturnsBadgeValue() throws Exception {
        when(notificationQueryService.unreadCount(eq(userId))).thenReturn(new UnreadCountResponse(3));

        mockMvc.perform(get("/api/v1/notifications/unread-count").principal(patientAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(3));
    }

    @Test
    void markReadReturnsUpdatedNotification() throws Exception {
        UUID notificationId = UUID.randomUUID();
        NotificationResponse response = new NotificationResponse(
                notificationId, NotificationType.REPORT_UPLOADED, "New report available",
                "A report was uploaded.", "REPORT", UUID.randomUUID(), true,
                Instant.parse("2026-08-31T04:30:00Z"));
        when(notificationService.markRead(eq(userId), eq(notificationId))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read").principal(patientAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    void streamRegistersSseEmitter() throws Exception {
        when(streamPublisher.register(userId)).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/notifications/stream").principal(patientAuth()))
                .andExpect(status().isOk());
    }

    private static UsernamePasswordAuthenticationToken patientAuth() {
        return UsernamePasswordAuthenticationToken.authenticated(
                PATIENT_EMAIL,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
    }
}
