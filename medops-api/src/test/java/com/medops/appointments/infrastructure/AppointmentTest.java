package com.medops.appointments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.medops.appointments.domain.AppointmentStatus;
import com.medops.shared.exception.InvalidRequestException;

class AppointmentTest {

    private static final Duration SLOT = Duration.ofMinutes(30);

    @Test
    void bookCreatesBookedVisit() {
        Instant start = Instant.parse("2026-09-01T03:30:00Z");
        Appointment appointment = Appointment.book(
                UUID.randomUUID(), UUID.randomUUID(), start, SLOT, "check-up");
        appointment.prePersist();

        assertThat(appointment.getId()).isNotNull();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(appointment.getEndsAt()).isEqualTo(start.plus(SLOT));
    }

    @Test
    void cancelFromBookedSucceedsBeforeStart() {
        Instant start = Instant.parse("2026-09-01T03:30:00Z");
        Appointment appointment = Appointment.book(
                UUID.randomUUID(), UUID.randomUUID(), start, SLOT, null);
        appointment.cancel(start.minusSeconds(60));
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelAfterStartIsRejected() {
        Instant start = Instant.parse("2026-09-01T03:30:00Z");
        Appointment appointment = Appointment.book(
                UUID.randomUUID(), UUID.randomUUID(), start, SLOT, null);
        assertThrows(InvalidRequestException.class, () -> appointment.cancel(start));
    }

    @Test
    void completeBeforeStartIsRejected() {
        Instant start = Instant.parse("2026-09-01T03:30:00Z");
        Appointment appointment = Appointment.book(
                UUID.randomUUID(), UUID.randomUUID(), start, SLOT, null);
        assertThrows(InvalidRequestException.class, () -> appointment.complete(start.minusSeconds(1)));
    }

    @Test
    void completeFromBookedSucceedsAtStart() {
        Instant start = Instant.parse("2026-09-01T03:30:00Z");
        Appointment appointment = Appointment.book(
                UUID.randomUUID(), UUID.randomUUID(), start, SLOT, null);
        appointment.complete(start);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }
}
