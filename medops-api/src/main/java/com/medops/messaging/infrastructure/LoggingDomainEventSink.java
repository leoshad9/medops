package com.medops.messaging.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.medops.messaging.domain.MedopsDomainEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "medops.messaging", name = "enabled", havingValue = "false")
public class LoggingDomainEventSink implements OutboundDomainEventSink {

    @Override
    public void send(MedopsDomainEvent event) {
        log.debug("Messaging disabled; dropped event type={} key={}", event.eventType(), event.topicKey());
    }
}
