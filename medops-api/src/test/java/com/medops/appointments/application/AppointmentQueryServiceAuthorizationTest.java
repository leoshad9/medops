package com.medops.appointments.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
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
import com.medops.patients.infrastructure.PatientProfile;

/**
 * BOLA: patients and doctors cannot open or mutate another actor's appointment by ID swap.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentQueryServiceAuthorizationTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentActorResolver actorResolver;
    @Mock
    private AppointmentResponseAssembler assembler;

    private Appointment appointment;

    @BeforeEach
    void setUp() {
        PatientProfile patientB = PatientProfile.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).mrn("B").fullName("B").build();
        UUID otherDoctorId = UUID.randomUUID();
        Instant start = Instant.parse("2026-09-15T04:30:00Z");
        appointment = Appointment.book(patientB.getId(), otherDoctorId, start, Duration.ofMinutes(30), null);
        appointment.prePersist();
    }

    @Test
    void getDeniesPatientWhenAppointmentBelongsToSomeoneElse() {
        AppointmentQueryService service = new AppointmentQueryService(appointmentRepository, actorResolver, assembler);
        when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(actorResolver.requireAppointmentParty(
                "patient.a@medops.dev", appointment.getPatientProfileId(), appointment.getDoctorProfileId()))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> service.get(appointment.getId(), "patient.a@medops.dev"));
        verify(assembler, never()).toResponse(any());
    }

    @Test
    void getDeniesDoctorWhenAppointmentBelongsToAnotherDoctor() {
        AppointmentQueryService service = new AppointmentQueryService(appointmentRepository, actorResolver, assembler);
        when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(actorResolver.requireAppointmentParty(
                "doctor.a@medops.dev", appointment.getPatientProfileId(), appointment.getDoctorProfileId()))
                .thenThrow(new AccessDeniedException("denied"));

        assertThrows(AccessDeniedException.class,
                () -> service.get(appointment.getId(), "doctor.a@medops.dev"));
        verify(assembler, never()).toResponse(any());
    }
}
