package com.medops.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.medops.notification.domain.Notification;
import com.medops.notification.domain.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA mapping for the {@code notifications} schema. Schema is created by Hibernate
 * {@code ddl-auto: update} (Flyway remains disabled by default, matching the rest of the app).
 */
@Entity
@Table(name = "notifications", schema = "notifications", indexes = {
        @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notifications_user_unread", columnList = "user_id, is_read")
}, uniqueConstraints = @UniqueConstraint(
        name = "uk_notifications_user_source_event",
        columnNames = {"user_id", "source_event_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class NotificationEntity {

    @Id
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @ToString.Include
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", columnDefinition = "UUID")
    private UUID referenceId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "source_event_id", columnDefinition = "UUID")
    private UUID sourceEventId;

    public static NotificationEntity from(Notification notification) {
        NotificationEntity entity = NotificationEntity.builder()
                .id(notification.id())
                .userId(notification.userId())
                .type(notification.type())
                .title(notification.title())
                .message(notification.message())
                .referenceType(notification.referenceType())
                .referenceId(notification.referenceId())
                .read(notification.read())
                .createdAt(notification.createdAt())
                .readAt(notification.readAt())
                .sourceEventId(notification.sourceEventId())
                .build();
        return entity;
    }

    public Notification toDomain() {
        return new Notification(
                id, userId, type, title, message, referenceType, referenceId, read, createdAt, readAt, sourceEventId);
    }

    /**
     * Marks the notification read. Returns {@code false} when it was already read, so
     * callers can skip the redundant write.
     */
    public boolean markRead(Instant now) {
        if (this.read) {
            return false;
        }
        this.read = true;
        this.readAt = now;
        return true;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
