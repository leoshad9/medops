package com.medops.assistant.infrastructure;

import java.time.Duration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantReply;
import com.medops.reports.infrastructure.AiClientProperties;
import com.medops.shared.exception.ServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/**
 * Wires the assistant AI client: stub when the AI service is disabled, otherwise the
 * HTTP client wrapped with the same resilience stack used by report summarization.
 * A dedicated breaker/retry instance is used so chat latency does not trip the
 * report summarizer's circuit breaker (and vice versa).
 */
@org.springframework.context.annotation.Configuration
public class AssistantClientConfiguration {

    private static final String ASSISTANT_CHAT = "assistantChat";
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Selects the local stub or a resilient HTTP assistant client.
     *
     * @param properties AI service connection settings
     * @param circuitBreakerRegistry registry for the assistant circuit breaker
     * @param retryRegistry registry for the assistant retry policy
     * @param timeLimiterRegistry registry for the assistant time limit
     * @return the configured assistant client
     */
    @org.springframework.context.annotation.Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(AssistantClient.class)
    AssistantClient assistantClient(
            AiClientProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        if (!properties.serviceEnabled()) {
            return new StubAssistantClient();
        }
        HttpAssistantClient http = new HttpAssistantClient(properties);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(ASSISTANT_CHAT);
        Retry retry = retryRegistry.retry(ASSISTANT_CHAT, RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(java.io.IOException.class, java.util.concurrent.TimeoutException.class,
                        org.springframework.web.client.ResourceAccessException.class)
                .build());
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(
                ASSISTANT_CHAT,
                TimeLimiterConfig.custom().timeoutDuration(CHAT_TIMEOUT).build());
        return new ResilientAssistantClient(http, circuitBreaker, retry, timeLimiter);
    }

    /**
     * Calls the MedOps FastAPI AI sidecar — not the LLM provider directly.
     */
    static final class HttpAssistantClient implements AssistantClient {

        private static final String CHAT_URI = "/ai/assistant/chat";

        private final RestClient restClient;

        /**
         * Creates a sidecar client from the configured service base URL.
         *
         * @param properties AI service connection settings
         */
        HttpAssistantClient(AiClientProperties properties) {
            ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect()
                    .build(ClientHttpRequestFactorySettings.defaults()
                            .withConnectTimeout(Duration.ofSeconds(5))
                            .withReadTimeout(CHAT_TIMEOUT));
            this.restClient = RestClient.builder()
                    .baseUrl(properties.serviceBaseUrl())
                    .requestFactory(factory)
                    .build();
        }

        /** Test-only constructor allowing a pre-configured {@link RestClient}. */
        HttpAssistantClient(RestClient restClient) {
            this.restClient = restClient;
        }

        /**
         * Posts a chat message to the AI sidecar and maps provider failures.
         *
         * @param userMessage the validated user message
         * @return the non-blank assistant reply
         */
        @Override
        public AssistantReply chat(String userMessage) {
            try {
                AssistantChatMessageResponse response = restClient.post()
                        .uri(CHAT_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(java.util.Map.of("message", userMessage))
                        .retrieve()
                        .body(AssistantChatMessageResponse.class);

                if (response == null || response.message() == null || response.message().isBlank()) {
                    throw new IllegalStateException("Empty assistant response");
                }
                return new AssistantReply(response.message().trim());
            } catch (RestClientResponseException ex) {
                String detail = ex.getResponseBodyAsString();
                String lower = detail == null ? "" : detail.toLowerCase();
                String message = "AI assistant unavailable";
                if (lower.contains("credit") || lower.contains("billing") || lower.contains("quota")) {
                    message = "AI provider has no credits remaining. Add billing credits, then try again.";
                } else if (ex.getStatusCode().value() == 429 || lower.contains("rate")) {
                    message = "AI provider rate limit exceeded. Please try again in a minute.";
                } else if (ex.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
                    message = "AI assistant timed out. Please try again.";
                }
                throw new ServiceUnavailableException(message, ex);
            }
        }

        record AssistantChatMessageResponse(String message) {
        }
    }
}
