package com.medops.shared.audit;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.medops.shared.util.LogMasking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Records immutable audit events for security-sensitive actions.
 *
 * <p>Non-transactional facade: the actual insert lives in {@link AuditEventWriter} so the
 * {@code REQUIRES_NEW} boundary is always crossed through the Spring proxy (a
 * self-invoked {@code @Transactional} method would silently join the caller's
 * transaction and roll back with it).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventWriter auditEventWriter;

    /**
     * Persists the event in its own transaction via {@link AuditEventWriter}.
     *
     * @param eventType the audit event category
     * @param subjectId the subject user id, may be {@code null}
     * @param subjectEmail the subject email, may be {@code null}
     */
    public void recordEvent(AuditEventType eventType, UUID subjectId, String subjectEmail) {
        auditEventWriter.recordEvent(eventType, subjectId, subjectEmail);
    }

    /**
     * Persists a <em>success</em> audit event in the caller's existing transaction.
     *
     * <p>Use this instead of {@link #recordEvent} for actions whose audit trail should only
     * commit when the enclosing action commits (e.g. an appointment booking that might still
     * roll back). Failure and security events should continue to use {@link #recordEvent}
     * so they survive a caller rollback.
     *
     * @param eventType the audit event category
     * @param subjectId the subject user id, may be {@code null}
     * @param subjectEmail the subject email, may be {@code null}
     */
    public void recordEventInCallerTx(AuditEventType eventType, UUID subjectId, String subjectEmail) {
        auditEventWriter.recordEventInCallerTx(eventType, subjectId, subjectEmail);
    }

    /**
     * Best-effort variant for events recorded <em>after</em> an irreversible action has
     * already completed (OTP email sent, OTP consumed, password changed). Failing the
     * user-facing request here would misrepresent reality to the client — e.g. a
     * forgot-password request returned as an error even though the OTP was delivered —
     * so persistence problems are logged loudly instead of propagated.
     *
     * @param eventType the audit event category
     * @param subjectId the subject user id, may be {@code null}
     * @param subjectEmail the subject email, may be {@code null}
     */
    public void recordEventBestEffort(AuditEventType eventType, UUID subjectId, String subjectEmail) {
        try {
            recordEvent(eventType, subjectId, subjectEmail);
        } catch (RuntimeException e) {
            log.error("Failed to persist audit event {} for subject {} - action already completed, "
                    + "responding normally to the client", eventType, LogMasking.maskEmail(subjectEmail), e);
        }
    }
}
