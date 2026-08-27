package com.medops.shared.audit;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medops.shared.web.RequestCorrelationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Records immutable audit events for security-sensitive actions.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Persists the event in its own transaction so it survives even when the caller's
     * transaction subsequently rolls back (e.g. a failed login attempt still needs to be
     * recorded even though the authentication exception aborts the enclosing transaction).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(AuditEventType eventType, UUID subjectId, String subjectEmail) {
        AuditEvent event = Objects.requireNonNull(AuditEvent.builder()
                .eventType(eventType)
                .subjectId(subjectId)
                .subjectEmail(subjectEmail)
                .correlationId(MDC.get(RequestCorrelationFilter.CORRELATION_ID_MDC_KEY))
                .build());
        auditEventRepository.save(event);
    }
}
