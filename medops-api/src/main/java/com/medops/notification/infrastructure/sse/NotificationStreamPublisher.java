package com.medops.notification.infrastructure.sse;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.notification.application.NotificationPublisher;
import com.medops.notification.domain.Notification;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Stream adapter for {@link NotificationPublisher}. Keeps one streaming connection per
 * authenticated user and pushes notifications as server-sent events. Emitters live in
 * memory only: missed notifications are served from PostgreSQL when the client next
 * subscribes or polls {@code GET /api/v1/notifications}.
 */
@Slf4j
@Component
public class NotificationStreamPublisher implements NotificationPublisher {

    private static final String KEEPALIVE_THREAD_NAME = "notification-sse-keepalive";
    private static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final Map<UUID, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService keepAliveExecutor;

    public NotificationStreamPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.keepAliveExecutor = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
        this.keepAliveExecutor.scheduleAtFixedRate(
                this::sendKeepAlives,
                KEEPALIVE_INTERVAL.toMillis(),
                KEEPALIVE_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Registers a fresh streaming emitter for the user.
     */
    public SseEmitter register(UUID userId) {
        return register(userId, new SseEmitter(0L));
    }

    /**
     * Registers an existing emitter (used by tests and callers that supply a custom emitter).
     */
    public SseEmitter register(UUID userId, SseEmitter emitter) {
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        subscribers.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        return emitter;
    }

    /**
     * Pushes a notification to every live connection of the user. Does not throw for
     * subscriber/transport failures; a broken subscriber is completed and removed so
     * the next publish skips it.
     */
    @Override
    public void publish(UUID userId, Notification notification) {
        Set<SseEmitter> emitters = subscribers.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = writePayload(notification);
        } catch (Exception ex) {
            log.error("Failed to serialize notification payload, skipping realtime publish userId={}", userId, ex);
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.id().toString())
                        .name("notification")
                        .data(payload));
            } catch (IOException | IllegalStateException ex) {
                // The only failures Spring's emitter can raise here: a broken/aborted
                // response (IOException) or a send after completion (IllegalStateException,
                // which also wraps any other write failure). Anything else is a programming
                // error that should surface rather than be masked as a dropped subscriber.
                log.warn("Dropping unresponsive SSE subscriber userId={}", userId, ex);
                remove(userId, emitter);
                completeQuietly(emitter);
            }
        }
    }

    /**
     * Sends a keepalive comment to every registered emitter. Package-private so tests can
     * trigger a round directly instead of waiting on the 30s schedule.
     */
    void sendKeepAlives() {
        for (Map.Entry<UUID, Set<SseEmitter>> entry : subscribers.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException | IllegalStateException ex) {
                    log.debug("Dropping unresponsive SSE subscriber during keepalive userId={}", entry.getKey(), ex);
                    remove(entry.getKey(), emitter);
                    completeQuietly(emitter);
                }
            }
        }
    }

    /**
     * Best-effort completion of an already-broken emitter. {@code complete()} can itself
     * throw once the underlying response has failed. The emitter has already been removed
     * from the subscriber map (or its {@code onCompletion} callback removes it), so a
     * failure here is logged at debug and ignored rather than allowed to propagate: out
     * of {@link #publish} it would abort delivery to the user's remaining subscribers,
     * and out of {@link #sendKeepAlives} it would kill the scheduled keepalive task for
     * good, since {@code scheduleAtFixedRate} suppresses future runs after any exception.
     */
    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ex) {
            log.debug("Ignoring failure while completing dropped SSE subscriber", ex);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        subscribers.computeIfPresent(userId, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    private String writePayload(Notification notification) {
        try {
            return objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize notification payload", ex);
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, KEEPALIVE_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void shutdown() {
        keepAliveExecutor.shutdownNow();
    }
}
