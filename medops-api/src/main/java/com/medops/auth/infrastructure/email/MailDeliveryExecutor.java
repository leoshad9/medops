package com.medops.auth.infrastructure.email;

import java.util.concurrent.Executor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Runs email delivery off the HTTP request thread. A synchronous Gmail SMTP
 * send costs ~4-5s (TLS handshake + auth + submission), which would otherwise
 * block the forgot-password / resend responses for that long.
 *
 * Wrap-only class (instead of exposing the raw {@link Executor} bean) so it
 * cannot be confused with Spring Boot's applicationTaskExecutor during
 * autowiring, and so tests can pass {@code Runnable::run} for synchronous,
 * deterministic execution.
 */
public class MailDeliveryExecutor {

    private final Executor delegate;

    /** Creates an executor for guarded mail delivery. */
    public MailDeliveryExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    /** Executes a mail operation with the configured resilience policy. */
    public void execute(Runnable task) {
        delegate.execute(task);
    }

    /** Called by Spring on context shutdown; safe for non-pool delegates. */
    public void shutdown() {
        if (delegate instanceof ThreadPoolTaskExecutor taskExecutor) {
            taskExecutor.shutdown();
        }
    }
}

