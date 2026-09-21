package com.medops.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.medops.assistant.domain.AssistantAppointment;
import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantContext;
import com.medops.assistant.domain.AssistantInvoice;
import com.medops.assistant.domain.AssistantLabReport;
import com.medops.assistant.domain.AssistantMedicalRecord;
import com.medops.assistant.domain.AssistantPrescription;
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

    /** Creates an HTTP client backed by a mock sidecar server. */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AssistantClientConfiguration.HttpAssistantClient(builder.build());
    }

    /** Verifies the sidecar request, including the appointment snapshot, and the reply mapping. */
    @Test
    void chatPostsMessageAndAppointmentContextToFastApiEndpoint() {
        AssistantAppointment context = new AssistantAppointment(
                "Wed, 23 Sep 2026 10:30", "BOOKED", "Dr. Rao", "Cardiology", "Room 3");
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(CHAT_URL))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .json("{\"message\":\"Hello\",\"time_zone\":\"Asia/Kolkata\",\"appointments\":["
                                + "{\"starts_at_local\":\"Wed, 23 Sep 2026 10:30\",\"status\":\"BOOKED\","
                                + "\"practitioner_name\":\"Dr. Rao\",\"specialty\":\"Cardiology\","
                                + "\"location\":\"Room 3\"}],\"lab_reports\":[],\"prescriptions\":[],"
                                + "\"invoices\":[],\"medical_records\":[]}", true))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Hi! How can I help?\"}"));

        AssistantReply reply = client.chat("Hello",
                AssistantContext.of(List.of(context), List.of(), List.of(), List.of(), List.of()), "Asia/Kolkata");

        assertThat(reply.text()).isEqualTo("Hi! How can I help?");
        server.verify();
    }

    /** Verifies that provider rate limiting becomes a friendly service failure. */
    @Test
    void chatMapsProviderRateLimitToServiceUnavailableWithFriendlyMessage() {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(CHAT_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"detail\":\"rate limited\"}"));

        assertThatThrownBy(() -> client.chat("Hello", AssistantContext.empty(), null))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("rate limit");
    }

    /** Verifies that a sidecar gateway timeout becomes a friendly service failure. */
    @Test
    void chatMapsGatewayTimeoutToServiceUnavailableWithFriendlyMessage() {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(CHAT_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.GATEWAY_TIMEOUT).body("{}"));

        assertThatThrownBy(() -> client.chat("Hello", AssistantContext.empty(), null))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("timed out");
    }

    /** Verifies that other sidecar failures become generic service failures. */
    @Test
    void chatMapsGenericProviderErrorToServiceUnavailable() {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(CHAT_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_GATEWAY).body("{}"));

        assertThatThrownBy(() -> client.chat("Hello", AssistantContext.empty(), null))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    /** Verifies that the disabled-service stub returns its deterministic reply. */
    @Test
    void stubReturnsDeterministicReplyWithoutAiService() {
        assertThat(new StubAssistantClient().chat("anything", AssistantContext.empty(), null).text())
                .contains("MedOps AI Assistant")
                .contains("appointments");
    }

    /** Verifies that the resilience wrapper retries a transient failure. */
    @Test
    void resilienceWrapperRetriesTransientFailureThenSucceeds() {
        AssistantClient flaky = new AssistantClient() {
            private int calls = 0;

            /** Simulates one transient failure followed by a successful reply. */
            @Override
            public AssistantReply chat(
                    String userMessage, AssistantContext context, String timeZone) {
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

        assertThat(resilient.chat("Hello", AssistantContext.empty(), null).text()).isEqualTo("recovered");
    }

    /** Verifies that the resilience wrapper times out a slow delegate. */
    @Test
    void resilienceWrapperTimesOutSlowDelegate() {
        AssistantClient slow = (userMessage, context, timeZone) -> {
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

        assertThatThrownBy(() -> resilient.chat("Hello", AssistantContext.empty(), null))
                .isInstanceOf(Exception.class);
    }

    /** Verifies the snake_case wire shape of the clinical, billing, and record context. */
    @Test
    void chatPostsClinicalBillingAndRecordContextInSnakeCase() {
        AssistantContext context = AssistantContext.of(
                List.of(),
                List.of(new AssistantLabReport(
                        "Wed, 23 Sep 2026 10:30", "Lab Panel: Lipid Profile", "NEW", "Dr. Rao", null, true,
                        "Cholesterol is slightly high.")),
                List.of(new AssistantPrescription(
                        "Tue, 22 Sep 2026 09:00", "Atorvastatin", "10 mg nightly", "ACTIVE", "Dr. Rao", null, 2)),
                List.of(new AssistantInvoice(
                        "Mon, 21 Sep 2026 08:00", "ISSUED", 12_000L, 2_000L, 10_000L, "2026-10-05", null)),
                List.of(new AssistantMedicalRecord(
                        "Mon, 21 Sep 2026 08:15", "Discharge Summary", "CLINICAL_DOCUMENT", "Dr. Rao", null, false,
                        null)));
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(CHAT_URL))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .json("{\"message\":\"Hello\",\"time_zone\":\"Asia/Kolkata\",\"appointments\":[],"
                                + "\"lab_reports\":[{\"created_at_local\":\"Wed, 23 Sep 2026 10:30\","
                                + "\"title\":\"Lab Panel: Lipid Profile\",\"status\":\"NEW\","
                                + "\"doctor_name\":\"Dr. Rao\",\"has_summary\":true,"
                                + "\"summary\":\"Cholesterol is slightly high.\"}],"
                                + "\"prescriptions\":[{\"created_at_local\":\"Tue, 22 Sep 2026 09:00\","
                                + "\"medication_name\":\"Atorvastatin\",\"dosage\":\"10 mg nightly\","
                                + "\"status\":\"ACTIVE\",\"doctor_name\":\"Dr. Rao\",\"refills_remaining\":2}],"
                                + "\"invoices\":[{\"created_at_local\":\"Mon, 21 Sep 2026 08:00\","
                                + "\"status\":\"ISSUED\",\"total_cents\":12000,\"paid_cents\":2000,"
                                + "\"balance_cents\":10000,\"due_date_local\":\"2026-10-05\"}],"
                                + "\"medical_records\":[{\"created_at_local\":\"Mon, 21 Sep 2026 08:15\","
                                + "\"title\":\"Discharge Summary\",\"type\":\"CLINICAL_DOCUMENT\","
                                + "\"doctor_name\":\"Dr. Rao\",\"has_summary\":false}]}", true))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Hi!\"}"));

        client.chat("Hello", context, "Asia/Kolkata");

        server.verify();
    }
}
