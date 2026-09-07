package com.medops.notification.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.notification.api.dto.NotificationPageResponse;
import com.medops.notification.api.dto.NotificationResponse;
import com.medops.notification.api.dto.UnreadCountResponse;
import com.medops.notification.infrastructure.persistence.NotificationEntity;
import com.medops.notification.infrastructure.persistence.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read side of the notification capability: feeds the bell badge and the notification list.
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public NotificationPageResponse list(UUID userId, int page, int size) {
        Page<NotificationEntity> result =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<NotificationResponse> items = result.getContent().stream()
                .map(NotificationEntity::toDomain)
                .map(NotificationResponse::from)
                .toList();
        return new NotificationPageResponse(items, page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UUID userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadFalse(userId));
    }
}
