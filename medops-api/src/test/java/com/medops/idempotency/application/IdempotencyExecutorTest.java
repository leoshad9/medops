package com.medops.idempotency.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.idempotency.infrastructure.IdempotencyProperties;
import com.medops.idempotency.infrastructure.InMemoryIdempotencyStore;
import com.medops.shared.exception.ConflictException;
import com.medops.shared.exception.InvalidRequestException;

class IdempotencyExecutorTest {

    private IdempotencyExecutor executor;

    @BeforeEach
    void setUp() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        executor = new IdempotencyExecutor(store, new IdempotencyProperties(Duration.ofHours(24)), new ObjectMapper());
    }

    @Test
    void executeWithoutKeyAlwaysRunsAction() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> action = () -> "r" + calls.incrementAndGet();

        assertThat(executor.execute("a", "op", null, "h", String.class, action)).isEqualTo("r1");
        assertThat(executor.execute("a", "op", "  ", "h", String.class, action)).isEqualTo("r2");
    }

    @Test
    void executeReplaysCompletedResultForSameKeyAndHash() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> action = () -> "created-" + calls.incrementAndGet();

        String first = executor.execute("patient@x", "appointments.book", "key-1", "hash-a", String.class, action);
        String second = executor.execute("patient@x", "appointments.book", "key-1", "hash-a", String.class, action);

        assertThat(first).isEqualTo("created-1");
        assertThat(second).isEqualTo("created-1");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void executeRejectsReusedKeyWithDifferentHash() {
        executor.execute("patient@x", "appointments.book", "key-1", "hash-a", String.class, () -> "one");

        assertThrows(ConflictException.class, () ->
                executor.execute("patient@x", "appointments.book", "key-1", "hash-b", String.class, () -> "two"));
    }

    @Test
    void executeAbandonsKeyWhenActionFails() {
        assertThrows(IllegalStateException.class, () ->
                executor.execute("patient@x", "appointments.book", "key-1", "hash-a", String.class, () -> {
                    throw new IllegalStateException("boom");
                }));

        String recovered = executor.execute(
                "patient@x", "appointments.book", "key-1", "hash-a", String.class, () -> "ok");
        assertThat(recovered).isEqualTo("ok");
    }

    @Test
    void executeRejectsInvalidKey() {
        assertThrows(InvalidRequestException.class, () ->
                executor.execute("a", "op", "bad key!", "h", String.class, () -> "x"));
    }
}
