package com.medops.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.assistant.api.dto.AssistantChatResponse;
import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantRateLimitException;
import com.medops.assistant.domain.AssistantReply;
import com.medops.auth.domain.User;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ServiceUnavailableException;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    private static final String EMAIL = "patient@medops.dev";

    @Mock
    private AssistantClient assistantClient;
    @Mock
    private RateLimiterStore rateLimiterStore;
    @Mock
    private AppointmentActorResolver actorResolver;
    @Mock
    private AuditService auditService;

    private AssistantService service;
    private User user;

    /** Creates the service under test and an authenticated user fixture. */
    @BeforeEach
    void setUp() {
        service = new AssistantService(assistantClient, rateLimiterStore, actorResolver, auditService);
        user = User.builder().id(UUID.randomUUID()).email(EMAIL).build();
    }

    /** Verifies that chat delegates to the client and returns its reply. */
    @Test
    void chatDelegatesToClientAndReturnsReply() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(assistantClient.chat("Hello")).thenReturn(new AssistantReply("Hi there!"));

        AssistantChatResponse response = service.chat(EMAIL, "Hello");

        assertThat(response.message()).isEqualTo("Hi there!");
        verify(assistantClient).chat("Hello");
    }

    /** Verifies that a successful chat records the resolved user identifier. */
    @Test
    void chatRecordsAuditEventWithResolvedUserId() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(assistantClient.chat("Hello")).thenReturn(new AssistantReply("Hi"));

        service.chat(EMAIL, "Hello");

        verify(auditService).recordEvent(AuditEventType.ASSISTANT_CHAT, user.getId(), EMAIL);
    }

    /** Verifies that the rate-limit key uses the server-resolved user identifier. */
    @Test
    void chatRateLimitKeyIsDerivedFromServerSideUserId() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(assistantClient.chat("Hello")).thenReturn(new AssistantReply("Hi"));

        service.chat(EMAIL, "Hello");

        // The key must be built from the server-resolved user id, never client input.
        verify(rateLimiterStore).tryAcquire("assistant:chat:" + user.getId(), 20, Duration.ofMinutes(5));
    }

    /** Verifies that an exhausted rate limit skips both the client and audit. */
    @Test
    void chatThrowsAndSkipsClientAndAuditWhenRateLimited() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.chat(EMAIL, "Hello"))
                .isInstanceOf(AssistantRateLimitException.class);

        verify(assistantClient, never()).chat(anyString());
        verify(auditService, never()).recordEvent(any(), any(), any());
    }

    /** Verifies that an unavailable rate-limit store fails closed. */
    @Test
    void chatFailsClosedWhenRateLimiterStoreUnavailable() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new ServiceUnavailableException("Rate limit store unavailable"));

        assertThatThrownBy(() -> service.chat(EMAIL, "Hello"))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(assistantClient, never()).chat(anyString());
    }

    /** Verifies that a failed assistant call is not recorded as successful. */
    @Test
    void chatDoesNotAuditWhenClientFails() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(assistantClient.chat("Hello")).thenThrow(new ServiceUnavailableException("AI assistant unavailable"));

        assertThatThrownBy(() -> service.chat(EMAIL, "Hello"))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(auditService, never()).recordEvent(any(), any(), any());
    }
}
