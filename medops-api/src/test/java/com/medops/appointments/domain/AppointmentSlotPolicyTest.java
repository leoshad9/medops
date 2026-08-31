package com.medops.appointments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class AppointmentSlotPolicyTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Duration SLOT = Duration.ofMinutes(30);
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);

    @Test
    void mondayGeneratesHalfHourSlotsUntilClose() {
        List<?> slots = AppointmentSlotPolicy.generateSlots(
                LocalDate.of(2026, 8, 31), IST, SLOT, OPEN, CLOSE);
        assertThat(slots).hasSize(16);
    }

    @Test
    void sundayIsClosed() {
        assertThat(AppointmentSlotPolicy.generateSlots(
                LocalDate.of(2026, 8, 30), IST, SLOT, OPEN, CLOSE)).isEmpty();
    }

    @Test
    void nineAmIstIsOnTheGrid() {
        var start = LocalDate.of(2026, 8, 31).atTime(9, 0).atZone(IST).toInstant();
        assertThat(AppointmentSlotPolicy.isOnGrid(start, IST, SLOT, OPEN, CLOSE)).isTrue();
    }
}
