package com.medops.appointments.infrastructure;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "medops.appointments")
public record AppointmentScheduleProperties(
        @NotBlank String zone,
        @Positive int slotMinutes,
        @NotNull LocalTime open,
        @NotNull LocalTime close) {

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    public Duration slotLength() {
        return Duration.ofMinutes(slotMinutes);
    }
}
