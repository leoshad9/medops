package com.medops.shared.audit;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medops.shared.web.RequestCorrelationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Transactional writer behind {@link AuditService}.
 *
 * <p>Split out so the {@code REQUIRES_NEW} boundary is always crossed through the Spring
 * proxy. Calling a {@code @Transactional} method via {@code this.} (self-invocation)
 * bypasses the proxy, silently folding the audit insert into the caller's transaction -
 * which is exactly the rollback-interference bug this split fixes.
 */
@Component
@RequiredArgsConstructor
public class AuditEventWriter {

    private final AuditEventRepository auditEventRepository;

    /**
     * Persists the event in its own transaction so it survives even when the caller's
     * transaction subsequently rolls back (e.g. a failed login attempt still needs to be
     * recorded even though the authentication exception aborts the enclosing transaction).
     *
     * <p>Caller must ensure this only runs for failure / security events — success events
     * should use {@link #recordEventInCallerTx} instead so the audit row rolls back with
     * the caller when the action itself did not commit.
     *
     * @param eventType the audit event category
     * @param subjectId the subject user id, may be {@code null}
     * @param subjectEmail the subject email, may be {@code null}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(AuditEventType eventType, UUID subjectId, String subjectEmail) {
        doRecordEvent(eventType, subjectId, subjectEmail);
    }

    /**
     * Persists the event in the caller's existing transaction.
     *
     * <p>Use this for <em>success</em> events (e.g. {@code APPOINTMENT_BOOKED}) so the audit
     * row is committed only when the enclosing action commits — a rolled-back booking must
     * not leave a committed audit trail.
     *
     * @param eventType the audit event category
     * @param subjectId the subject user id, may be {@code null}
     * @param subjectEmail the subject email, may be {@code null}
     */
    @Transactional
    public void recordEventInCallerTx(AuditEventType eventType, UUID subjectId, String subjectEmail) {
        doRecordEvent(eventType, subjectId, subjectEmail);
    }

    /** Persists one audit event in an independent transaction. */
    private void doRecordEvent(AuditEventType eventType, UUID subjectId, String subjectEmail) {
        AuditEvent event = Objects.requireNonNull(AuditEvent.builder()
                .eventType(eventType)
                .subjectId(subjectId)
                .subjectEmail(subjectEmail)
                .correlationId(MDC.get(RequestCorrelationFilter.CORRELATION_ID_MDC_KEY))
                .build());
        auditEventRepository.save(event);
    }
}
