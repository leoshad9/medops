package com.medops.appointments.infrastructure;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppointmentScheduleProperties.class)
public class AppointmentsConfiguration {

    /**
     * IANA zone used to bucket clinical instants into clinic days (e.g. "today's
     * schedule"). Configurable so deployments in other regions do not see IST
     * times; defaults to the original clinic zone.
     */
    public static final String CLINIC_TIME_ZONE_PROPERTY = "medops.clinic.time-zone";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId clinicTimeZone(
            @org.springframework.beans.factory.annotation.Value(
                    "${" + CLINIC_TIME_ZONE_PROPERTY + ":Asia/Kolkata}") String zoneId) {
        return ZoneId.of(zoneId);
    }
}
