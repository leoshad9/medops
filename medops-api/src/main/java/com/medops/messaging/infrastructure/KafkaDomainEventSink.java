package com.medops.messaging.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.medops.messaging.domain.MedopsDomainEvent;
import com.medops.messaging.events.AppointmentBookedEvent;
import com.medops.messaging.events.ReportUploadedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "medops.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class KafkaDomainEventSink implements OutboundDomainEventSink {

    private final KafkaTemplate<String, DomainEventMessage> kafkaTemplate;
    private final MessagingProperties properties;

    @Override
    public void send(MedopsDomainEvent event) {
        if (event instanceof AppointmentBookedEvent booked) {
            DomainEventMessage message = DomainEventMessage.appointmentBooked(
                    booked.appointmentId(), booked.occurredAt());
            kafkaTemplate.send(properties.appointmentsTopic(), booked.topicKey(), message);
            log.info("Published AppointmentBooked appointmentId={}", booked.appointmentId());
            return;
        }
        if (event instanceof ReportUploadedEvent uploaded) {
            DomainEventMessage message = DomainEventMessage.reportUploaded(
                    uploaded.reportId(), uploaded.occurredAt());
            kafkaTemplate.send(properties.reportsTopic(), uploaded.topicKey(), message);
            log.info("Published ReportUploaded reportId={}", uploaded.reportId());
            return;
        }
        log.warn("Ignoring unsupported domain event type={}", event.eventType());
    }
}
