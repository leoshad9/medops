package com.medops.notification.api;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.notification.infrastructure.sse.NotificationStreamPublisher;

import lombok.RequiredArgsConstructor;

/**
 * Server-sent events endpoint. Clients authenticate with the existing bearer JWT via
 * {@code fetch()} streaming and receive {@code notification} events in real time.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationStreamController {

    private final NotificationStreamPublisher streamPublisher;
    private final AppointmentActorResolver actorResolver;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public SseEmitter stream(Authentication authentication) {
        return streamPublisher.register(actorResolver.requireActiveUser(authentication.getName()).getId());
    }
}
