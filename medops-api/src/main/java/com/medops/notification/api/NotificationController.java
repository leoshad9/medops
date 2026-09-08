package com.medops.notification.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.notification.api.dto.MarkAllReadResponse;
import com.medops.notification.api.dto.NotificationPageResponse;
import com.medops.notification.api.dto.NotificationResponse;
import com.medops.notification.api.dto.UnreadCountResponse;
import com.medops.notification.application.NotificationQueryService;
import com.medops.notification.application.NotificationService;
import com.medops.shared.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * REST endpoints for the notification bell: list, unread count and mark-as-read.
 * Realtime delivery lives on {@link NotificationStreamController}.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationService notificationService;
    private final AppointmentActorResolver actorResolver;

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<NotificationPageResponse>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationQueryService.list(requireUserId(authentication), page, size)));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationQueryService.unreadCount(requireUserId(authentication))));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            Authentication authentication, @PathVariable UUID notificationId) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.markRead(requireUserId(authentication), notificationId)));
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<MarkAllReadResponse>> markAllRead(Authentication authentication) {
        int marked = notificationService.markAllRead(requireUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success(
                new MarkAllReadResponse(marked), "All notifications marked as read"));
    }

    private UUID requireUserId(Authentication authentication) {
        return actorResolver.requireActiveUser(authentication.getName()).getId();
    }
}
