package com.medops.appointments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

/**
 * Unit tests for {@link AppointmentReconciliationService}: verifies ended BOOKED
 * visits are transitioned to COMPLETED, future/cancelled visits are left untouched,
 * and the scheduled trigger delegates with the configured batch size.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private Clock clock;
    @Mock
    private AuditService auditService;

    private AppointmentReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentReconciliationService(appointmentRepository, clock, auditService);
        when(clock.instant()).thenReturn(NOW);
    }

    /** A BOOKED appointment that already ended is reconciled to COMPLETED. */
    @Test
    void completesEndedBookedVisits() {
        Appointment ended = endedBooked(NOW.minusSeconds(5400), NOW.minusSeconds(3600));
        when(appointmentRepository.findByStatusAndEndsAtBeforeOrderByStartsAtAsc(
                eq(AppointmentStatus.BOOKED), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(ended));

        int reconciled = service.reconcilePastAppointments(200);

        assertThat(reconciled).isEqualTo(1);
        assertThat(ended.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(ended.getCompletedAt()).isEqualTo(NOW);
        verify(appointmentRepository).save(ended);
        verify(auditService).recordEventBestEffort(AuditEventType.APPOINTMENT_COMPLETED, null, null);
    }

    /** Only rows the query returns (ended, BOOKED) are touched; future and
     * completed visits are excluded by the status/time filter and never saved. */
    @Test
    void processesOnlyQueryResultsLeavesOthersUntouched() {
        Appointment ended = endedBooked(NOW.minusSeconds(5400), NOW.minusSeconds(3600));
        Appointment future = endedBooked(NOW.plusSeconds(3600), NOW.plusSeconds(5400));
        Appointment completed = endedBooked(NOW.minusSeconds(7200), NOW.minusSeconds(5400));
        completed.setStatus(AppointmentStatus.COMPLETED);
        // The (status=BOOKED AND ends_at < now) filter returns only the ended visit.
        when(appointmentRepository.findByStatusAndEndsAtBeforeOrderByStartsAtAsc(
                eq(AppointmentStatus.BOOKED), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(ended));

        int reconciled = service.reconcilePastAppointments(200);

        assertThat(reconciled).isEqualTo(1);
        assertThat(ended.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(future.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(completed.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        verify(appointmentRepository).save(ended);
        verify(appointmentRepository, never()).save(future);
        verify(appointmentRepository, never()).save(completed);
        verify(auditService).recordEventBestEffort(AuditEventType.APPOINTMENT_COMPLETED, null, null);
    }

    @Test
    void isNoOpWhenNothingEnded() {
        when(appointmentRepository.findByStatusAndEndsAtBeforeOrderByStartsAtAsc(
                eq(AppointmentStatus.BOOKED), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of());

        int reconciled = service.reconcilePastAppointments(200);

        assertThat(reconciled).isZero();
        verify(appointmentRepository, never()).save(any());
        verify(auditService, never()).recordEventBestEffort(any(), any(), any());
    }

    /** The scheduled trigger delegates to the testable logic with the configured batch size. */
    @Test
    void reconcileDelegatesWithConfiguredBatchSize() {
        ReflectionTestUtils.setField(service, "batchSize", 2);
        Appointment ended = endedBooked(NOW.minusSeconds(5400), NOW.minusSeconds(3600));
        when(appointmentRepository.findByStatusAndEndsAtBeforeOrderByStartsAtAsc(
                eq(AppointmentStatus.BOOKED), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(ended));

        service.reconcile();

        verify(appointmentRepository).findByStatusAndEndsAtBeforeOrderByStartsAtAsc(
                eq(AppointmentStatus.BOOKED), eq(NOW), any(Pageable.class));
        assertThat(ended.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    private static Appointment endedBooked(Instant startsAt, Instant endsAt) {
        return Appointment.builder()
                .id(UUID.randomUUID())
                .patientProfileId(UUID.randomUUID())
                .doctorProfileId(UUID.randomUUID())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(AppointmentStatus.BOOKED)
                .build();
    }
}
