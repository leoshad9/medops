package com.medops.notification.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.notification.domain.Notification;
import com.medops.notification.domain.NotificationType;

class NotificationStreamPublisherTest {

    @Test
    void publishDeliversToSubscribedUser() {
        NotificationStreamPublisher publisher = new NotificationStreamPublisher(objectMapper());
        UUID userId = UUID.randomUUID();
        RecordingSseEmitter emitter = new RecordingSseEmitter();

        publisher.register(userId, emitter);
        publisher.publish(userId, notification());

        assertThat(emitter.sentSseEvents()).hasSize(1);
    }

    @Test
    void publishSkipsUsersWithoutSubscriber() {
        NotificationStreamPublisher publisher = new NotificationStreamPublisher(objectMapper());

        publisher.publish(UUID.randomUUID(), notification());

        // Publish must be a no-op (never throw) when nobody is connected.
        assertThatCode(() -> publisher.publish(UUID.randomUUID(), notification()))
                .doesNotThrowAnyException();
    }

    @Test
    void publishDoesNotDeliverToOtherUsers() {
        NotificationStreamPublisher publisher = new NotificationStreamPublisher(objectMapper());
        UUID userId = UUID.randomUUID();
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        publisher.register(userId, emitter);

        publisher.publish(UUID.randomUUID(), notification());
        publisher.publish(userId, notification());

        assertThat(emitter.sentSseEvents()).hasSize(1);
    }

    @Test
    void publishSwallowsSerializationFailures() throws JsonProcessingException {
        ObjectMapper broken = mock(ObjectMapper.class);
        doThrow(new RuntimeException("serialization failed")).when(broken).writeValueAsString(any());
        NotificationStreamPublisher publisher = new NotificationStreamPublisher(broken);
        UUID userId = UUID.randomUUID();
        publisher.register(userId, new RecordingSseEmitter());

        assertThatCode(() -> publisher.publish(userId, notification()))
                .doesNotThrowAnyException();
    }

    @Test
    void publishDropsSubscriberWhoseSendFails() {
        NotificationStreamPublisher publisher = new NotificationStreamPublisher(objectMapper());
        UUID userId = UUID.randomUUID();
        FailingSendSseEmitter emitter = new FailingSendSseEmitter();
        publisher.register(userId, emitter);

        assertThatCode(() -> publisher.publish(userId, notification()))
                .doesNotThrowAnyException();

        assertThat(emitter.completed).isTrue();
    }

    @Test
    void publishDropsBrokenSubscriberWithoutAffectingOthers() {
        NotificationStreamPublisher publisher = new NotificationStreamPublisher(objectMapper());
        UUID userId = UUID.randomUUID();
        BrokenSseEmitter broken = new BrokenSseEmitter();
        RecordingSseEmitter healthy = new RecordingSseEmitter();
        publisher.register(userId, broken);
        publisher.register(userId, healthy);

        // send() and even complete() throw for the broken emitter; publish must still
        // deliver to the healthy subscriber instead of aborting the loop.
        assertThatCode(() -> publisher.publish(userId, notification()))
                .doesNotThrowAnyException();

        assertThat(healthy.sentSseEvents()).hasSize(1);

        // The broken emitter is dropped, so only the healthy one receives the next event.
        publisher.publish(userId, notification());

        assertThat(healthy.sentSseEvents()).hasSize(2);
    }

    @Test
    void keepAliveDropsSubscriberWhoseSendFails() {
        NotificationStreamPublisher publisher = new NotificationStreamPublisher(objectMapper());
        UUID userId = UUID.randomUUID();
        FailingSendSseEmitter emitter = new FailingSendSseEmitter();
        publisher.register(userId, emitter);

        assertThatCode(publisher::sendKeepAlives)
                .doesNotThrowAnyException();

        assertThat(emitter.completed).isTrue();
    }

    @Test
    void keepAliveDropsBrokenSubscriberWithoutAffectingOthers() {
        NotificationStreamPublisher publisher = new NotificationStreamPublisher(objectMapper());
        UUID userId = UUID.randomUUID();
        BrokenSseEmitter broken = new BrokenSseEmitter();
        RecordingSseEmitter healthy = new RecordingSseEmitter();
        publisher.register(userId, broken);
        publisher.register(userId, healthy);

        // send() and even complete() throw for the broken emitter; the keepalive round
        // must still reach the healthy subscriber instead of aborting the loop.
        assertThatCode(publisher::sendKeepAlives)
                .doesNotThrowAnyException();

        assertThat(healthy.sentSseEvents()).hasSize(1);
    }

    @Test
    void keepAliveRemovesSubscriberEvenWhenCompleteThrows() {
        NotificationStreamPublisher publisher = new NotificationStreamPublisher(objectMapper());
        UUID userId = UUID.randomUUID();
        CountingBrokenSseEmitter broken = new CountingBrokenSseEmitter();
        publisher.register(userId, broken);

        publisher.sendKeepAlives();
        publisher.sendKeepAlives();

        // send() and complete() both throw, so the onCompletion callback never fires:
        // the keepalive round must remove the emitter explicitly, otherwise the dead
        // connection is retried (and leaks) on every round.
        assertThat(broken.sendAttempts.get()).isEqualTo(1);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private static Notification notification() {
        return new Notification(
                UUID.randomUUID(), UUID.randomUUID(), NotificationType.APPOINTMENT_BOOKED,
                "Appointment confirmed", "Your appointment is confirmed.",
                "APPOINTMENT", UUID.randomUUID(), false, Instant.now(), null, null);
    }

    /**
     * Captures {@link SseEmitter} sends without requiring a live servlet response.
     */
    private static final class RecordingSseEmitter extends SseEmitter {

        private final List<SseEmitter.SseEventBuilder> sent = new ArrayList<>();

        @Override
        public void send(@NonNull SseEmitter.SseEventBuilder builder) {
            sent.add(builder);
        }

        List<SseEmitter.SseEventBuilder> sentSseEvents() {
            return sent;
        }
    }

    /**
     * Emulates a subscriber whose connection broke mid-stream: every send fails, but the
     * final {@code complete()} cleanup still succeeds.
     */
    private static final class FailingSendSseEmitter extends SseEmitter {

        private boolean completed;

        @Override
        public void send(@NonNull SseEmitter.SseEventBuilder builder) {
            throw new IllegalStateException("connection broken");
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    /**
     * Emulates a fully broken subscriber: every send fails and even the final
     * {@code complete()} cleanup throws, as can happen once the underlying
     * servlet response has already been aborted.
     */
    private static final class BrokenSseEmitter extends SseEmitter {

        @Override
        public void send(@NonNull SseEmitter.SseEventBuilder builder) {
            throw new IllegalStateException("connection broken");
        }

        @Override
        public void complete() {
            throw new IllegalStateException("response already aborted");
        }
    }

    /**
     * {@link BrokenSseEmitter} that also counts send attempts, so tests can assert the
     * publisher actually removed it from the subscriber map after a failed round.
     */
    private static final class CountingBrokenSseEmitter extends SseEmitter {

        private final AtomicInteger sendAttempts = new AtomicInteger();

        @Override
        public void send(@NonNull SseEmitter.SseEventBuilder builder) {
            sendAttempts.incrementAndGet();
            throw new IllegalStateException("connection broken");
        }

        @Override
        public void complete() {
            throw new IllegalStateException("response already aborted");
        }
    }
}
