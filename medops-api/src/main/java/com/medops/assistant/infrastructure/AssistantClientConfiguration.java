package com.medops.assistant.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.medops.assistant.domain.AssistantAppointment;
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
            // The AI sidecar runs uvicorn, which rejects the h2c upgrade request the
            // JDK HttpClient sends by default on plaintext HTTP ("Unsupported upgrade
            // request", after which the body is mis-parsed and FastAPI answers 422).
            // Pin HTTP/1.1 on the client itself so no upgrade is ever attempted.
            HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(CHAT_TIMEOUT);
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
         * Posts a chat message plus the caller's own appointment snapshot to the AI
         * sidecar and maps provider failures. The message, the snapshot, and the reply
         * are never logged.
         *
         * @param userMessage the validated user message
         * @param appointments LLM-safe snapshot of the caller's own upcoming appointments
         * @param timeZone IANA zone the appointment times were rendered in, may be null
         * @return the non-blank assistant reply
         */
        @Override
        public AssistantReply chat(
                String userMessage, List<AssistantAppointment> appointments, String timeZone) {
            try {
                AssistantChatMessageResponse response = restClient.post()
                        .uri(CHAT_URI)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody(userMessage, appointments, timeZone))
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

        /**
         * Builds the sidecar request body. The sidecar's schema uses snake_case keys,
         * and optional values are omitted rather than sent as {@code null}.
         *
         * @param userMessage the validated user message
         * @param appointments LLM-safe appointment snapshot, never null
         * @param timeZone IANA zone the snapshot times were rendered in, may be null
         * @return the JSON payload to post
         */
        private static Map<String, Object> requestBody(
                String userMessage, List<AssistantAppointment> appointments, String timeZone) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", userMessage);
            putIfPresent(body, "time_zone", timeZone);
            body.put("appointments",
                    appointments.stream().map(HttpAssistantClient::contextPayload).toList());
            return body;
        }

        /** Maps one appointment to the sidecar's appointment-context shape. */
        private static Map<String, Object> contextPayload(AssistantAppointment appointment) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("starts_at_local", appointment.startsAtLocal());
            item.put("status", appointment.status());
            putIfPresent(item, "practitioner_name", appointment.practitionerName());
            putIfPresent(item, "specialty", appointment.specialty());
            putIfPresent(item, "location", appointment.location());
            return item;
        }

        /** Adds an optional key only when it carries a value. */
        private static void putIfPresent(Map<String, Object> target, String key, String value) {
            if (value != null) {
                target.put(key, value);
            }
        }

        record AssistantChatMessageResponse(String message) {
        }
    }
}
