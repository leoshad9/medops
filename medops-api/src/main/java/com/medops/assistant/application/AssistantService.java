package com.medops.assistant.application;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.assistant.api.dto.AssistantChatResponse;
import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantRateLimitException;
import com.medops.assistant.domain.AssistantReply;
import com.medops.auth.domain.User;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates a single assistant chat turn: rate limiting, actor resolution,
 * AI client invocation, and auditing. Never logs the user message or the reply.
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final String RATE_LIMIT_KEY_PREFIX = "assistant:chat:";
    private static final int MAX_CHATS_PER_WINDOW = 20;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(5);

    private final AssistantClient assistantClient;
    private final RateLimiterStore rateLimiterStore;
    private final AppointmentActorResolver actorResolver;
    private final AuditService auditService;

    /**
     * Processes one chat turn for the authenticated user.
     *
     * <p>Identity comes exclusively from the authenticated principal (email);
     * no user/patient identifiers are ever accepted from the client.
     *
     * @param email the authenticated user's email
     * @param message the validated, non-blank user message
     * @return the assistant reply wrapped in the API response payload
     */
    public AssistantChatResponse chat(String email, String message) {
        User user = actorResolver.requireActiveUser(email);

        // Fail-closed: RateLimiterStore implementations throw
        // ServiceUnavailableException when the backing store is unreachable.
        if (!rateLimiterStore.tryAcquire(RATE_LIMIT_KEY_PREFIX + user.getId(),
                MAX_CHATS_PER_WINDOW, RATE_LIMIT_WINDOW)) {
            throw new AssistantRateLimitException();
        }

        AssistantReply reply = assistantClient.chat(message);

        // Success event only; the message and reply text are never persisted or logged.
        auditService.recordEvent(AuditEventType.ASSISTANT_CHAT, user.getId(), email);
        return new AssistantChatResponse(reply.text());
    }
}
