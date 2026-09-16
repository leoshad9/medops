package com.medops.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Test-only wiring: the {@code test} profile excludes RedisAutoConfiguration (and
 * all Redis-backed components are {@code @ConditionalOnProperty(medops.redis.enabled)}),
 * so no {@link StringRedisTemplate} bean exists. The password-reset services
 * constructor-inject one, so supply a lazy localhost template purely so the
 * Spring context can load. No test drives the reset flow through this bean — the
 * services are unit-tested against a mocked template.
 */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class TestRedisBeanConfiguration {

    /** Redis default port; only ever wired, never connected to in tests. */
    private static final int REDIS_PORT = 6379;

    /** Provides a lazy Redis template for application tests. */
    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        // LettuceConnectionFactory connects lazily; constructing it never opens a
        // socket, so a context that only wires the bean stays offline.
        return new StringRedisTemplate(
                new LettuceConnectionFactory("localhost", REDIS_PORT));
    }
}

