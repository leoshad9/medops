package com.medops.notification.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.notification.api.dto.NotificationResponse;
import com.medops.notification.domain.Notification;
import com.medops.notification.infrastructure.persistence.NotificationEntity;
import com.medops.notification.infrastructure.persistence.NotificationRepository;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Command side of the notification capability. Persists a notification (idempotently per
 * source event) and pushes it in real time through a {@link NotificationPublisher}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPublisher notificationPublisher;

    /**
     * Stores and streams a notification. Returns an empty Optional when the source event
     * was already processed for this user (at-least-once Kafka delivery).
     */
    @Transactional
    public Optional<Notification> create(CreateNotification command) {
        UUID sourceEventId = command.sourceEventId();
        if (sourceEventId != null
                && notificationRepository.existsByUserIdAndSourceEventId(command.userId(), sourceEventId)) {
            log.debug("Notification already processed userId={} sourceEventId={}", command.userId(), sourceEventId);
            return Optional.empty();
        }

        Instant now = Instant.now();
        Notification notification = new Notification(
                UUID.randomUUID(), command.userId(), command.type(), command.title(), command.message(),
                command.referenceType(), command.referenceId(), false, now, null, sourceEventId);
        try {
            notificationRepository.saveAndFlush(NotificationEntity.from(notification));
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate notification suppressed userId={} sourceEventId={}", command.userId(), sourceEventId);
            return Optional.empty();
        }
        notificationPublisher.publish(command.userId(), notification);
        return Optional.of(notification);
    }

    /**
     * Marks one of the user's notifications as read. 404 when the id does not belong to
     * the caller.
     */
    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        NotificationEntity entity = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (entity.markRead(Instant.now())) {
            notificationRepository.save(entity);
        }
        return NotificationResponse.from(entity.toDomain());
    }
}
