package com.medops.assistant.infrastructure;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantReply;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;

/**
 * Decorates the assistant HTTP client with circuit breaker, retry, and time limiter,
 * mirroring {@code ResilientReportSummarizer}.
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
     * @return the assistant reply
     */
    @Override
    public AssistantReply chat(String userMessage) {
        Supplier<AssistantReply> supplier = () -> delegate.chat(userMessage);
        Supplier<AssistantReply> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        Supplier<AssistantReply> withRetry = Retry.decorateSupplier(retry, withCb);
        Callable<AssistantReply> withTimeout = TimeLimiter.decorateFutureSupplier(
                timeLimiter,
                () -> CompletableFuture.supplyAsync(withRetry, EXECUTOR));
        try {
            return withTimeout.call();
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Assistant chat failed", ex);
        }
    }
}
