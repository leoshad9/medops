package com.medops.infrastructure;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * When Redis/Kafka are enabled, connection targets must resolve (defaults cover local compose).
 */
@Component
@RequiredArgsConstructor
public class ExternalInfrastructureGuard implements ApplicationRunner {

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            return;
        }
        boolean redisEnabled = environment.getProperty("medops.redis.enabled", Boolean.class, true);
        boolean messagingEnabled = environment.getProperty("medops.messaging.enabled", Boolean.class, true);
        if (redisEnabled && !StringUtils.hasText(environment.getProperty("spring.data.redis.host"))) {
            throw new IllegalStateException("spring.data.redis.host / REDIS_HOST is required when Redis is enabled");
        }
        if (messagingEnabled && !StringUtils.hasText(environment.getProperty("spring.kafka.bootstrap-servers"))) {
            throw new IllegalStateException(
                    "spring.kafka.bootstrap-servers / KAFKA_BOOTSTRAP is required when messaging is enabled");
        }
    }
}
