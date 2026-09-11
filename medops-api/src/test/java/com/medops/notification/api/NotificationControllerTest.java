package com.medops.notification.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.lang.NonNull;
import org.springframework.security.test.context.support.WithMockUser;
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
@AutoConfigureMockMvc
@SuppressWarnings({"null", "Nullable", "ConstantConditions"})
class NotificationControllerTest {

    private static final @NonNull String PATIENT_EMAIL = "patient@medops.dev";

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

    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void listReturnsPagedNotifications() throws Exception {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());

        NotificationResponse notification = new NotificationResponse(
                UUID.randomUUID(), NotificationType.APPOINTMENT_BOOKED, "Appointment confirmed",
                "Your appointment is confirmed.", "APPOINTMENT", UUID.randomUUID(), false,
                Instant.parse("2026-08-31T04:30:00Z"));
        NotificationPageResponse page = new NotificationPageResponse(List.of(notification), 0, 20, 1);
        when(notificationQueryService.list(userId, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].title").value("Appointment confirmed"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void unreadCountReturnsBadgeValue() throws Exception {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());

        when(notificationQueryService.unreadCount(userId)).thenReturn(new UnreadCountResponse(3));

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(3));
    }

    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void markReadReturnsUpdatedNotification() throws Exception {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());

        UUID notificationId = UUID.randomUUID();
        NotificationResponse response = new NotificationResponse(
                notificationId, NotificationType.REPORT_UPLOADED, "New report available",
                "A report was uploaded.", "REPORT", UUID.randomUUID(), true,
                Instant.parse("2026-08-31T04:30:00Z"));
        when(notificationService.markRead(userId, notificationId)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void markAllReadReturnsMarkedCount() throws Exception {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());

        when(notificationService.markAllRead(userId)).thenReturn(4);

        mockMvc.perform(post("/api/v1/notifications/read-all").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.marked").value(4));
    }

    @Test
    @WithMockUser(username = PATIENT_EMAIL, roles = "PATIENT")
    void streamRegistersSseEmitter() throws Exception {
        when(actorResolver.requireActiveUser(PATIENT_EMAIL))
                .thenReturn(User.builder().id(userId).email(PATIENT_EMAIL).build());

        when(streamPublisher.register(userId)).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/notifications/stream"))
                .andExpect(status().isOk());
    }
}
