package com.medops.ratelimit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.medops.cache.infrastructure.RedisRateLimitScripts;

class RedisRateLimitScriptsTest {

    @Test
    void scriptIncrementsAndSetsExpiryAtomicallyOnFirstAttempt() {
        String script = RedisRateLimitScripts.INCREMENT_WITH_EXPIRY.getScriptAsString();
        assertThat(script).contains("INCR", "if count == 1", "PEXPIRE", "return count");
    }
}
