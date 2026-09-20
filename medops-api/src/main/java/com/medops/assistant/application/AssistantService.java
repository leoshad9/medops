package com.medops.assistant.application;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.appointments.application.AppointmentQueryService;
import com.medops.assistant.api.dto.AssistantChatResponse;
import com.medops.assistant.domain.AssistantAppointment;
import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantRateLimitException;
import com.medops.assistant.domain.AssistantReply;
import com.medops.auth.domain.User;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates a single assistant chat turn: rate limiting, actor resolution,
 * appointment-context enrichment, AI client invocation, and auditing. Never logs
 * the user message, the reply, or the appointment snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final String RATE_LIMIT_KEY_PREFIX = "assistant:chat:";
    private static final int MAX_CHATS_PER_WINDOW = 20;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(5);

    /** Upper bound on the snapshot, mirroring the sidecar's context limit. */
    private static final int MAX_CONTEXT_APPOINTMENTS = 10;
    /** Mirrors the sidecar's per-field limit so the payload can never be rejected. */
    private static final int MAX_CONTEXT_FIELD_CHARS = 200;
    private static final ZoneId UTC_FALLBACK = ZoneId.of("UTC");
    private static final DateTimeFormatter CONTEXT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm", Locale.ENGLISH);

    private final AssistantClient assistantClient;
    private final RateLimiterStore rateLimiterStore;
    private final AppointmentActorResolver actorResolver;
    private final AppointmentQueryService appointmentQueryService;
    private final AuditService auditService;

    /**
     * Processes one chat turn for the authenticated user.
     *
     * <p>Identity comes exclusively from the authenticated principal (email);
     * no user/patient identifiers are ever accepted from the client. The reply is
     * grounded in a bounded, LLM-safe snapshot of the caller's own upcoming
     * appointments; when that snapshot cannot be read the chat still proceeds
     * without context rather than failing.
     *
     * @param email the authenticated user's email
     * @param message the validated, non-blank user message
     * @return the assistant reply wrapped in the API response payload
     */
    public AssistantChatResponse chat(String email, String message) {
        return chat(email, message, null);
    }

    /**
     * Processes one chat turn for the authenticated user.
     *
     * <p>Identity comes exclusively from the authenticated principal (email);
     * no user/patient identifiers are ever accepted from the client. The reply is
     * grounded in a bounded, LLM-safe snapshot of the caller's own upcoming
     * appointments; when that snapshot cannot be read the chat still proceeds
     * without context rather than failing.
     *
     * @param email the authenticated user's email
     * @param message the validated, non-blank user message
     * @param timeZone optional IANA zone from the client, used only to render this
     *                 user's own appointment times; never trusted for identity
     * @return the assistant reply wrapped in the API response payload
     */
    public AssistantChatResponse chat(String email, String message, String timeZone) {
        User user = actorResolver.requireActiveUser(email);

        // Fail-closed: RateLimiterStore implementations throw
        // ServiceUnavailableException when the backing store is unreachable.
        if (!rateLimiterStore.tryAcquire(RATE_LIMIT_KEY_PREFIX + user.getId(),
                MAX_CHATS_PER_WINDOW, RATE_LIMIT_WINDOW)) {
            throw new AssistantRateLimitException();
        }

        ZoneId zone = resolveZone(timeZone);
        List<AssistantAppointment> appointments = upcomingAppointments(email, zone);

        AssistantReply reply = assistantClient.chat(message, appointments, zone.getId());

        // Success event only; the message and reply text are never persisted or logged.
        auditService.recordEvent(AuditEventType.ASSISTANT_CHAT, user.getId(), email);
        return new AssistantChatResponse(reply.text());
    }

    /**
     * Reads the caller's own upcoming appointments through the standard authorized
     * query path, so a patient sees only their appointments and a doctor only their
     * own schedule.
     *
     * <p>Best-effort by design: any failure yields an empty snapshot plus a warning,
     * so degraded enrichment can never turn a chat into a server error. Only
     * LLM-safe fields are mapped, and times are pre-rendered in the caller's zone so
     * the model never performs timezone arithmetic.
     *
     * @param email the authenticated user's email
     * @param zone the zone the snapshot times must be rendered in
     * @return a soonest-first snapshot of at most {@value #MAX_CONTEXT_APPOINTMENTS}
     *         upcoming appointments, or an empty list when unavailable
     */
    private List<AssistantAppointment> upcomingAppointments(String email, ZoneId zone) {
        try {
            Instant now = Instant.now();
            return appointmentQueryService.list(email, null, null, null, 0, MAX_CONTEXT_APPOINTMENTS)
                    .items().stream()
                    .filter(appointment -> appointment.startsAt().isAfter(now))
                    .sorted(Comparator.comparing(AppointmentResponse::startsAt))
                    .limit(MAX_CONTEXT_APPOINTMENTS)
                    .map(appointment -> toContext(appointment, zone))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Assistant appointment context unavailable; continuing without it", ex);
            return List.of();
        }
    }

    /** Maps one appointment to the identifier-free projection sent to the AI service. */
    private AssistantAppointment toContext(AppointmentResponse appointment, ZoneId zone) {
        return new AssistantAppointment(
                CONTEXT_TIME_FORMAT.withZone(zone).format(appointment.startsAt()),
                appointment.status().name(),
                clip(appointment.doctorName()),
                clip(appointment.specialty()),
                clip(appointment.location()));
    }

    /** Resolves the client-supplied zone, falling back to UTC when absent or unknown. */
    private static ZoneId resolveZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return UTC_FALLBACK;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException ex) {
            // Client input only: a bad zone must never fail the chat nor reach the model.
            return UTC_FALLBACK;
        }
    }

    /** Collapses whitespace and truncates to the sidecar's per-field limit. */
    private static String clip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= MAX_CONTEXT_FIELD_CHARS
                ? collapsed
                : collapsed.substring(0, MAX_CONTEXT_FIELD_CHARS);
    }
}
