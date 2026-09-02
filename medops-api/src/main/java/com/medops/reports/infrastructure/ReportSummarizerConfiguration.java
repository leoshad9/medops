package com.medops.reports.infrastructure;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.medops.reports.domain.ReportSummary;
import com.medops.reports.domain.ReportSummarizer;
import com.medops.shared.exception.ServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

@Configuration
public class ReportSummarizerConfiguration {

    private static final String REPORT_SUMMARIZER = "reportSummarizer";

    @Bean
    @ConditionalOnMissingBean(ReportSummarizer.class)
    ReportSummarizer reportSummarizer(
            AiClientProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        if (!properties.serviceEnabled()) {
            return new StubReportSummarizer();
        }
        FastApiReportSummarizer http = new FastApiReportSummarizer(properties);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(REPORT_SUMMARIZER);
        Retry retry = retryRegistry.retry(REPORT_SUMMARIZER, RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(IOException.class, TimeoutException.class, ResourceAccessException.class)
                .build());
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(
                REPORT_SUMMARIZER,
                TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(20)).build());
        return new ResilientReportSummarizer(http, circuitBreaker, retry, timeLimiter);
    }

    static final class StubReportSummarizer implements ReportSummarizer {

        private static final int BYTES_PER_PAGE_ESTIMATE = 50_000;

        @Override
        public ReportSummary summarize(UUID reportId, byte[] pdfContent) {
            int pagesEstimate = Math.max(1, pdfContent.length / BYTES_PER_PAGE_ESTIMATE);
            return new ReportSummary(
                    "Plain-language overview (stub): this lab PDF is about "
                            + pagesEstimate
                            + " page(s). This is not a diagnosis or treatment advice.");
        }
    }

    /**
     * Applies TimeLimiter + CircuitBreaker + Retry only around the outbound HTTP call.
     */
    static final class ResilientReportSummarizer implements ReportSummarizer {

        private final ReportSummarizer delegate;
        private final CircuitBreaker circuitBreaker;
        private final Retry retry;
        private final TimeLimiter timeLimiter;

        ResilientReportSummarizer(
                ReportSummarizer delegate,
                CircuitBreaker circuitBreaker,
                Retry retry,
                TimeLimiter timeLimiter) {
            this.delegate = delegate;
            this.circuitBreaker = circuitBreaker;
            this.retry = retry;
            this.timeLimiter = timeLimiter;
        }

        @Override
        public ReportSummary summarize(UUID reportId, byte[] pdfContent) {
            Supplier<ReportSummary> supplier = () -> delegate.summarize(reportId, pdfContent);
            Supplier<ReportSummary> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
            Supplier<ReportSummary> withRetry = Retry.decorateSupplier(retry, withCb);
            Callable<ReportSummary> withTimeout = TimeLimiter.decorateFutureSupplier(
                    timeLimiter,
                    () -> java.util.concurrent.CompletableFuture.supplyAsync(withRetry));
            try {
                return withTimeout.call();
            } catch (Exception ex) {
                if (ex instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("Report summarization failed", ex);
            }
        }
    }

    /**
     * Calls the MedOps FastAPI AI sidecar — not the LLM provider directly.
     */
    static final class FastApiReportSummarizer implements ReportSummarizer {

        private final RestClient restClient;

        FastApiReportSummarizer(AiClientProperties properties) {
            ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect()
                    .build(ClientHttpRequestFactorySettings.defaults()
                            .withConnectTimeout(Duration.ofSeconds(5))
                            .withReadTimeout(Duration.ofSeconds(20)));
            this.restClient = RestClient.builder()
                    .baseUrl(properties.serviceBaseUrl())
                    .requestFactory(factory)
                    .build();
        }

        @Override
        public ReportSummary summarize(UUID reportId, byte[] pdfContent) {
            Map<String, Object> body = Map.of(
                    "content_base64", Base64.getEncoder().encodeToString(pdfContent),
                    "content_type", "application/pdf");

            try {
                AiSummaryResponse response = restClient.post()
                        .uri("/ai/reports/{reportId}/summary", reportId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(AiSummaryResponse.class);

                if (response == null || response.summary() == null || response.summary().isBlank()) {
                    throw new IllegalStateException("Empty summarizer response");
                }
                return new ReportSummary(response.summary().trim());
            } catch (RestClientResponseException ex) {
                String detail = ex.getResponseBodyAsString();
                String lower = detail == null ? "" : detail.toLowerCase();
                String message = "AI summarizer unavailable";
                if (lower.contains("credit") || lower.contains("billing") || lower.contains("quota")) {
                    message = "OpenAI account has no credits remaining. Add billing credits, then try again.";
                } else if (ex.getStatusCode().value() == 429 || lower.contains("rate")) {
                    message = "AI provider rate limit exceeded. Please try again in a minute.";
                } else if (ex.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
                    message = "AI summarizer timed out. Please try again.";
                }
                throw new ServiceUnavailableException(message, ex);
            }
        }

        record AiSummaryResponse(String summary) {
        }
    }
}
