package com.medops.shared.audit;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Immutable record of a security-sensitive action. Never updated after creation - it
 * complements entity timestamps and is not a substitute for soft deletion or other
 * lifecycle tracking. No setters are generated on purpose.
 */
@Entity
@Table(name = "audit_events", schema = "audit", indexes = {
        @Index(name = "idx_audit_events_subject_id", columnList = "subject_id"),
        @Index(name = "idx_audit_events_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_events_occurred_at", columnList = "occurred_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class AuditEvent {

    @Id
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    @ToString.Include
    private AuditEventType eventType;

    /** The subject the event is about (e.g. the acting user), when known. */
    @Column(name = "subject_id")
    private UUID subjectId;

    /**
     * The email involved in the attempt, even when it doesn't resolve to a subject (e.g. a
     * failed login for an unknown address) - needed to detect credential-stuffing/enumeration
     * patterns. Never a password, token, or other secret.
     */
    @Column(name = "subject_email", length = 255)
    private String subjectEmail;

    @Column(name = "correlation_id", length = 100)
    @ToString.Include
    private String correlationId;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    @ToString.Include
    private ZonedDateTime occurredAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
