package com.medops.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.medops.notification.domain.Notification;
import com.medops.notification.domain.NotificationType;
import com.medops.notification.infrastructure.persistence.NotificationEntity;
import com.medops.notification.infrastructure.persistence.NotificationRepository;
import com.medops.shared.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPublisher notificationPublisher;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, notificationPublisher);
    }

    private static CreateNotification command() {
        return new CreateNotification(
                UUID.randomUUID(), NotificationType.APPOINTMENT_BOOKED,
                "Appointment confirmed", "Your appointment is confirmed.",
                "APPOINTMENT", UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void createPersistsAndBroadcasts() {
        CreateNotification command = command();
        when(notificationRepository.existsByUserIdAndSourceEventId(command.userId(), command.sourceEventId()))
                .thenReturn(false);

        Optional<Notification> created = service.create(command);

        assertThat(created).isPresent();
        assertThat(created.orElseThrow().title()).isEqualTo(command.title());
        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(command.userId());
        assertThat(captor.getValue().getSourceEventId()).isEqualTo(command.sourceEventId());
        verify(notificationPublisher).publish(eq(command.userId()), any(Notification.class));
    }

    @Test
    void createSkipsAlreadyProcessedSourceEvent() {
        CreateNotification command = command();
        when(notificationRepository.existsByUserIdAndSourceEventId(command.userId(), command.sourceEventId()))
                .thenReturn(true);

        Optional<Notification> created = service.create(command);

        assertThat(created).isEmpty();
        verify(notificationRepository, never()).saveAndFlush(any());
        verify(notificationPublisher, never()).publish(any(), any());
    }

    @Test
    void createSuppressesDuplicateCommitRace() {
        CreateNotification command = command();
        when(notificationRepository.existsByUserIdAndSourceEventId(command.userId(), command.sourceEventId()))
                .thenReturn(false);
        when(notificationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        Optional<Notification> created = service.create(command);

        assertThat(created).isEmpty();
        verify(notificationPublisher, never()).publish(any(), any());
    }

    @Test
    void markReadUpdatesEntity() {
        UUID userId = UUID.randomUUID();
        NotificationEntity entity = NotificationEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(NotificationType.REPORT_UPLOADED)
                .title("New report")
                .message("A report was uploaded.")
                .referenceType("REPORT")
                .referenceId(UUID.randomUUID())
                .createdAt(Instant.now())
                .sourceEventId(UUID.randomUUID())
                .build();
        when(notificationRepository.findByIdAndUserId(entity.getId(), userId)).thenReturn(Optional.of(entity));

        var response = service.markRead(userId, entity.getId());

        assertThat(response.read()).isTrue();
        assertThat(entity.isRead()).isTrue();
        assertThat(entity.getReadAt()).isNotNull();
        verify(notificationRepository).save(entity);
    }

    @Test
    void markReadThrowsWhenNotificationNotOwnedByUser() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(userId, notificationId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(notificationRepository, never()).save(any());
    }
}
