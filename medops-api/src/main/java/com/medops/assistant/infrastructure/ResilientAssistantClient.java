package com.medops.assistant.infrastructure;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantContext;
import com.medops.assistant.domain.AssistantReply;
import com.medops.shared.exception.ServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;

/**
 * Decorates the assistant HTTP client with circuit breaker, retry, and time limiter,
 * mirroring {@code ResilientReportSummarizer}. Time-limiter timeouts, an open circuit
 * breaker, and the delegate's failures are surfaced as {@link ServiceUnavailableException}
 * so the API answers 503 with an actionable message instead of an unmapped 500.
 */
public class ResilientAssistantClient implements AssistantClient {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "assistant-chat-resilience");
        thread.setDaemon(true);
        return thread;
    });

    private final AssistantClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;

    /**
     * Creates a client that applies the supplied resilience policies.
     *
     * @param delegate underlying assistant client
     * @param circuitBreaker assistant circuit breaker
     * @param retry assistant retry policy
     * @param timeLimiter assistant call time limit
     */
    ResilientAssistantClient(
            AssistantClient delegate, CircuitBreaker circuitBreaker, Retry retry, TimeLimiter timeLimiter) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.timeLimiter = timeLimiter;
    }

    /**
     * Executes a chat call through the circuit breaker, retry, and time limiter.
     *
     * @param userMessage the validated user message
     * @param context LLM-safe context snapshot for the same user
     * @param timeZone IANA zone the snapshot times were rendered in
     * @return the assistant reply
     */
    @Override
    public AssistantReply chat(
            String userMessage, AssistantContext context, String timeZone) {
        Supplier<AssistantReply> supplier = () -> delegate.chat(userMessage, context, timeZone);
        Supplier<AssistantReply> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        Supplier<AssistantReply> withRetry = Retry.decorateSupplier(retry, withCb);
        Callable<AssistantReply> withTimeout = TimeLimiter.decorateFutureSupplier(
                timeLimiter,
                () -> CompletableFuture.supplyAsync(withRetry, EXECUTOR));
        try {
            return withTimeout.call();
        } catch (TimeoutException ex) {
            // Budget exhausted (provider busy / sidecar still retrying) — fail fast
            // with an actionable 503 instead of hanging the chat panel for 20 s.
            throw new ServiceUnavailableException(
                    "AI assistant is taking too long. Please try again.", ex);
        } catch (CallNotPermittedException ex) {
            throw new ServiceUnavailableException(
                    "AI assistant is temporarily unavailable. Please try again in a moment.", ex);
        } catch (ExecutionException ex) {
            // The resilience stack runs inside the async future: surface the real
            // cause (e.g. the HTTP client's ServiceUnavailableException) instead of
            // wrapping it in IllegalStateException, which the handler reports as 500.
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof ServiceUnavailableException unavailable) {
                throw unavailable;
            }
            if (cause instanceof CallNotPermittedException notPermitted) {
                throw new ServiceUnavailableException(
                        "AI assistant is temporarily unavailable. Please try again in a moment.",
                        notPermitted);
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Assistant chat failed", cause);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Assistant chat failed", ex);
        }
    }
}
