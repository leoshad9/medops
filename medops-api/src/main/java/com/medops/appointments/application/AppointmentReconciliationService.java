package com.medops.appointments.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Server-side guard for the appointment lifecycle. Transitions {@code BOOKED}
 * appointments that have already ended ({@code endsAt < now}) to {@code COMPLETED},
 * so past visits are never persisted as actionable "Upcoming" rows. Because every
 * consumer (patient dashboard, doctor dashboard, AI assistant context) reads the
 * same {@code status} column, correcting it here fixes the classification
 * everywhere at once.
 *
 * <p>No-show distinction is intentionally deferred: a missed visit is reconciled to
 * {@code COMPLETED} rather than silently left stale as {@code BOOKED}. When the
 * clinic needs to separate missed from attended visits, a {@code MISSED} status can
 * be layered onto this same reconciliation pass.
 *
 * <p>Disabled in the {@code test} profile
 * ({@code medops.appointments.reconciliation.enabled=false}) so the scheduled task
 * never mutates test data; the transition logic is unit-tested directly via
 * {@link #reconcilePastAppointments(int)}.
 */
@Service
@ConditionalOnProperty(
        name = "medops.appointments.reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AppointmentReconciliationService {

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;
    private final AuditService auditService;

    @Value("${medops.appointments.reconciliation.batch-size:200}")
    private int batchSize;

    /**
     * Periodic trigger. Delegates to {@link #reconcilePastAppointments(int)} so the
     * transition logic stays unit-testable without a live scheduler or a Spring
     * context.
     */
    @Scheduled(
            fixedDelayString = "${medops.appointments.reconciliation.fixed-delay-ms:60000}",
            initialDelayString = "${medops.appointments.reconciliation.initial-delay-ms:30000}")
    @Transactional
    public void reconcile() {
        int reconciled = reconcilePastAppointments(batchSize);
        if (reconciled > 0) {
            log.info("Reconciled {} past appointments to COMPLETED", reconciled);
        }
    }

    /**
     * Transitions up to {@code max} {@code BOOKED} appointments whose {@code endsAt}
     * lies in the past to {@code COMPLETED}. Idempotent: already-completed and
     * cancelled rows are excluded by the {@code BOOKED} status filter, so a second
     * pass never re-processes them.
     *
     * @param max upper bound of appointments reconciled in this pass
     * @return the number of appointments transitioned in this pass
     */
    @Transactional
    public int reconcilePastAppointments(int max) {
        Instant now = clock.instant();
        List<Appointment> ended = appointmentRepository
                .findByStatusAndEndsAtBeforeOrderByStartsAtAsc(
                        AppointmentStatus.BOOKED, now, PageRequest.of(0, max));
        for (Appointment appointment : ended) {
            appointment.complete(now);
            appointmentRepository.save(appointment);
            // Automated, system-driven completion: no human actor subject. The
            // appointment's own completedAt/updatedAt timestamps anchor the trail.
            auditService.recordEventBestEffort(
                    AuditEventType.APPOINTMENT_COMPLETED, null, null);
        }
        return ended.size();
    }
}
