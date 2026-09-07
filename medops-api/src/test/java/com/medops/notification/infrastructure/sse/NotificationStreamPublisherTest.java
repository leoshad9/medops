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

import org.junit.jupiter.api.Test;
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

        // No exception expected: publish is a no-op when nobody is connected.
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
        public void send(SseEmitter.SseEventBuilder builder) {
            sent.add(builder);
        }

        List<SseEmitter.SseEventBuilder> sentSseEvents() {
            return sent;
        }
    }
}
