package com.medops.appointments.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.appointments.infrastructure.AppointmentScheduleProperties;
import com.medops.shared.audit.AuditService;

@ExtendWith(MockitoExtension.class)
class AppointmentTransitionServiceAuthorizationTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentActorResolver actorResolver;
    @Mock
    private AppointmentResponseAssembler assembler;
    @Mock
    private BookAppointmentService bookAppointmentService;
    @Mock
    private AuditService auditService;

    private Appointment otherPatientsAppointment;

    @BeforeEach
    void setUp() {
        Instant start = Instant.parse("2026-09-15T04:30:00Z");
        otherPatientsAppointment = Appointment.book(
                UUID.randomUUID(), UUID.randomUUID(), start, Duration.ofMinutes(30), null);
        otherPatientsAppointment.prePersist();
    }

    @Test
    void cancelDeniesPatientWhenAppointmentBelongsToSomeoneElse() {
        AppointmentScheduleProperties schedule = new AppointmentScheduleProperties(
                "Asia/Kolkata", 30, LocalTime.of(9, 0), LocalTime.of(17, 0));
        AppointmentTransitionService service = new AppointmentTransitionService(
                appointmentRepository,
                actorResolver,
                assembler,
                bookAppointmentService,
                schedule,
                auditService,
                Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), ZoneOffset.UTC));
        when(appointmentRepository.findById(otherPatientsAppointment.getId()))
                .thenReturn(Optional.of(otherPatientsAppointment));
        when(actorResolver.requireAppointmentParty(
                "patient.a@medops.dev",
                otherPatientsAppointment.getPatientProfileId(),
                otherPatientsAppointment.getDoctorProfileId()))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> service.cancel(otherPatientsAppointment.getId(), "patient.a@medops.dev"));

        verify(appointmentRepository, never()).save(any());
        verify(auditService, never()).recordEvent(any(), any(), any());
    }
}
