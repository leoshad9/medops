package com.medops.shared.audit;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medops.shared.web.RequestCorrelationFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Records immutable audit events for security-sensitive actions.
 */
@Slf4j
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

    /**
     * Best-effort variant for events recorded <em>after</em> an irreversible action has
     * already completed (OTP email sent, OTP consumed, password changed). Failing the
     * user-facing request here would misrepresent reality to the client — e.g. a
     * forgot-password request returned as an error even though the OTP was delivered —
     * so persistence problems are logged loudly instead of propagated.
     */
    public void recordEventBestEffort(AuditEventType eventType, UUID subjectId, String subjectEmail) {
        try {
            recordEvent(eventType, subjectId, subjectEmail);
        } catch (RuntimeException e) {
            log.error("Failed to persist audit event {} for subject {} - action already completed, "
                    + "responding normally to the client", eventType, subjectEmail, e);
        }
    }
}
