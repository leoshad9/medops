package com.medops.appointments.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Clinic timetable used to generate bookable instants. Sunday is closed. Slots are
 * contiguous from {@code open} (inclusive) until {@code close} (exclusive of a slot
 * that would finish after close).
 */
public final class AppointmentSlotPolicy {

    private AppointmentSlotPolicy() {
    }

    public static List<Instant> generateSlots(
            LocalDate date,
            ZoneId zone,
            Duration slotLength,
            LocalTime open,
            LocalTime close) {
        if (DayOfWeek.SUNDAY.equals(date.getDayOfWeek())) {
            return List.of();
        }

        List<Instant> slots = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.of(date, open);
        LocalDateTime lastStart = LocalDateTime.of(date, close).minus(slotLength);
        while (!cursor.isAfter(lastStart)) {
            slots.add(cursor.atZone(zone).toInstant());
            cursor = cursor.plus(slotLength);
        }
        return List.copyOf(slots);
    }

    public static boolean isOnGrid(
            Instant startsAt,
            ZoneId zone,
            Duration slotLength,
            LocalTime open,
            LocalTime close) {
        LocalDate date = startsAt.atZone(zone).toLocalDate();
        return generateSlots(date, zone, slotLength, open, close).contains(startsAt);
    }
}
