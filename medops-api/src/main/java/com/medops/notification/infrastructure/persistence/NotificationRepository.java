package com.medops.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID notificationId, UUID userId);

    boolean existsByUserIdAndSourceEventId(UUID userId, UUID sourceEventId);

    /** Bulk-marks every unread notification for the user; returns the number updated. */
    @Modifying
    @Query("update NotificationEntity n set n.read = true, n.readAt = :now "
            + "where n.userId = :userId and n.read = false")
    int markAllReadForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
