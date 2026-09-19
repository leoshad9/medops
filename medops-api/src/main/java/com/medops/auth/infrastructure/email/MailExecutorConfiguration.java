package com.medops.auth.infrastructure.email;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * Bounded pool for background email delivery. Sized for the low volume of
 * password-reset emails; bounded so a slow/stuck SMTP host cannot pile up
 * unbounded threads or queue entries.
 *
 * <p>The {@link ThreadPoolTaskExecutor} is intentionally NOT registered as a bean:
 * registering any {@link Executor}-typed bean would make Spring Boot's
 * {@code applicationTaskExecutor} back off (the same reason {@link MailDeliveryExecutor}
 * is a wrap-only class). The executor is instead held as a private field and shared by
 * the wrapper and {@link AsyncConfigurer}, so unqualified {@code @Async} methods resolve
 * to this same bounded pool.
 */
@Slf4j
@Configuration
@EnableAsync
public class MailExecutorConfiguration implements AsyncConfigurer {

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;

    private final ThreadPoolTaskExecutor mailExecutor = buildExecutor();

    private static ThreadPoolTaskExecutor buildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        // Under saturation the caller's thread delivers the email itself rather
        // than dropping it — backpressure that preserves the delivery guarantee.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Drain queued sends on graceful shutdown instead of discarding them.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Provides the mail-delivery executor bean. */
    @Bean(destroyMethod = "shutdown")
    public MailDeliveryExecutor mailDeliveryExecutor() {
        return new MailDeliveryExecutor(mailExecutor);
    }

    @Override
    public Executor getAsyncExecutor() {
        return mailExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // The @Async confirmation sender handles EmailSendingException itself; this
        // only guards against anything unexpected escaping the proxy.
        return (throwable, method, params) ->
                log.error("Unexpected async failure in mail task {}", method.getName(), throwable);
    }
}

