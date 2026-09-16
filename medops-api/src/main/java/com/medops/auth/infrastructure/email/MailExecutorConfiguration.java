package com.medops.auth.infrastructure.email;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded pool for background email delivery. Sized for the low volume of
 * password-reset emails; bounded so a slow/stuck SMTP host cannot pile up
 * unbounded threads or queue entries.
 */
@Configuration
public class MailExecutorConfiguration {

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 100;

    /** Provides the mail-delivery executor bean. */
    @Bean(destroyMethod = "shutdown")
    public MailDeliveryExecutor mailDeliveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        // Under saturation the caller's thread delivers the email itself rather
        // than dropping it — backpressure that preserves the delivery guarantee.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return new MailDeliveryExecutor(executor);
    }
}

