package com.medops.messaging.infrastructure;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.medops.messaging.domain.DomainEventPublisher;
import com.medops.messaging.domain.MedopsDomainEvent;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridges use-case publish calls onto Spring's after-commit event phase, then delegates to Kafka
 * (or a no-op sink when messaging is disabled).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final OutboundDomainEventSink outboundDomainEventSink;
    private final MeterRegistry meterRegistry;

    @Override
    public void publishAfterCommit(MedopsDomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void forwardAfterCommit(MedopsDomainEvent event) {
        try {
            outboundDomainEventSink.send(event);
        } catch (RuntimeException ex) {
            log.error("Failed to publish domain event type={} key={}", event.eventType(), event.topicKey(), ex);
            meterRegistry.counter("domain.event.publish.failure", "event_type", event.eventType()).increment();
        }
    }
}
