package com.medops.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantReply;
import com.medops.shared.exception.ServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

/**
 * Wire-level tests for {@code AssistantClientConfiguration.HttpAssistantClient} and the
 * resilience wrapper, using Spring's {@link MockRestServiceServer} (no live HTTP).
 */
class AssistantClientInfrastructureTest {

    private static final String BASE_URL = "http://medops-ai:8000";
    private static final String CHAT_URL = BASE_URL + "/ai/assistant/chat";

    private MockRestServiceServer server;
    private AssistantClientConfiguration.HttpAssistantClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AssistantClientConfiguration.HttpAssistantClient(builder.build());
    }

    @Test
    void chatPostsMessageToFastApiEndpointAndParsesReply() {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(CHAT_URL))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .json("{\"message\":\"Hello\"}"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Hi! How can I help?\"}"));

        AssistantReply reply = client.chat("Hello");

        assertThat(reply.text()).isEqualTo("Hi! How can I help?");
        server.verify();
    }

    @Test
    void chatMapsProviderRateLimitToServiceUnavailableWithFriendlyMessage() {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(CHAT_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"detail\":\"rate limited\"}"));

        assertThatThrownBy(() -> client.chat("Hello"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void chatMapsGatewayTimeoutToServiceUnavailableWithFriendlyMessage() {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(CHAT_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.GATEWAY_TIMEOUT).body("{}"));

        assertThatThrownBy(() -> client.chat("Hello"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void chatMapsGenericProviderErrorToServiceUnavailable() {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(CHAT_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_GATEWAY).body("{}"));

        assertThatThrownBy(() -> client.chat("Hello"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void stubReturnsDeterministicReplyWithoutAiService() {
        assertThat(new StubAssistantClient().chat("anything").text())
                .contains("MedOps AI Assistant")
                .contains("appointments");
    }

    @Test
    void resilienceWrapperRetriesTransientFailureThenSucceeds() {
        AssistantClient flaky = new AssistantClient() {
            private int calls = 0;

            @Override
            public AssistantReply chat(String userMessage) {
                calls++;
                if (calls == 1) {
                    throw new ServiceUnavailableException("transient", new IOException("boom"));
                }
                return new AssistantReply("recovered");
            }
        };
        // Retry only ServiceUnavailableException here; production retries IO/timeout
        // exceptions (ResourceAccessException etc.) which never surface as
        // RestClientResponseException from the HTTP client.
        Retry retry = Retry.of("test", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(ServiceUnavailableException.class)
                .build());
        ResilientAssistantClient resilient = new ResilientAssistantClient(
                flaky,
                CircuitBreaker.of("assistant-retry-test", CircuitBreakerConfig.ofDefaults()),
                retry,
                TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build()));

        assertThat(resilient.chat("Hello").text()).isEqualTo("recovered");
    }

    @Test
    void resilienceWrapperTimesOutSlowDelegate() {
        AssistantClient slow = userMessage -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new AssistantReply("late");
        };
        ResilientAssistantClient resilient = new ResilientAssistantClient(
                slow,
                CircuitBreaker.of("assistant-timeout-test", CircuitBreakerConfig.ofDefaults()),
                Retry.of("assistant-timeout-test", RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(50)).build()));

        assertThatThrownBy(() -> resilient.chat("Hello")).isInstanceOf(Exception.class);
    }
}