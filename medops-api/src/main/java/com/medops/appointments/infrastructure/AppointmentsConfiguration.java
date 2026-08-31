package com.medops.appointments.infrastructure;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppointmentScheduleProperties.class)
public class AppointmentsConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
