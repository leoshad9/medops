package com.medops.appointments.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.api.dto.RescheduleAppointmentRequest;
import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.appointments.infrastructure.AppointmentScheduleProperties;
import com.medops.messaging.domain.DomainEventPublisher;
import com.medops.messaging.events.AppointmentCancelledEvent;
import com.medops.messaging.events.AppointmentRescheduledEvent;
import com.medops.shared.audit.AuditService;

@ExtendWith(MockitoExtension.class)
class AppointmentTransitionServiceEventTest {

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
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private static final Instant START = Instant.parse("2026-09-15T04:30:00Z");
    private static final Instant RESCHEDULED_START = Instant.parse("2026-09-15T05:00:00Z");
    private static final UUID APPOINTMENT_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");
    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444");
    private static final UUID DOCTOR_ID = UUID.fromString("cccccccc-1111-2222-3333-444444444444");
    private static final UUID ACTOR_USER_ID = UUID.fromString("dddddddd-1111-2222-3333-444444444444");
    private static final String EMAIL = "patient@medops.dev";

    private AppointmentTransitionService service() {
        AppointmentScheduleProperties schedule = new AppointmentScheduleProperties(
                "Asia/Kolkata", 30, LocalTime.of(9, 0), LocalTime.of(17, 0));
        return new AppointmentTransitionService(
                appointmentRepository,
                actorResolver,
                assembler,
                bookAppointmentService,
                schedule,
                auditService,
                domainEventPublisher,
                Clock.fixed(Instant.parse("2026-09-15T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void cancelPublishesAppointmentCancelledEvent() {
        Appointment appointment = Appointment.book(PATIENT_ID, DOCTOR_ID, START, Duration.ofMinutes(30), null);
        appointment.setId(APPOINTMENT_ID);
        appointment.prePersist();
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(actorResolver.requireAppointmentParty(EMAIL, PATIENT_ID, DOCTOR_ID)).thenReturn(ACTOR_USER_ID);
        when(appointmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assembler.toResponse(any())).thenReturn(new AppointmentResponse(
                APPOINTMENT_ID, PATIENT_ID, DOCTOR_ID, "Patient", null, null, null,
                "Doctor", null, START, START.plusSeconds(1800),
                com.medops.appointments.domain.AppointmentStatus.CANCELLED, null, null));

        service().cancel(APPOINTMENT_ID, EMAIL);

        verify(domainEventPublisher).publishAfterCommit(any(AppointmentCancelledEvent.class));
        verify(auditService).recordEvent(
                com.medops.shared.audit.AuditEventType.APPOINTMENT_CANCELLED, ACTOR_USER_ID, EMAIL);
    }

    @Test
    void reschedulePublishesAppointmentRescheduledEvent() {
        Appointment appointment = Appointment.book(PATIENT_ID, DOCTOR_ID, START, Duration.ofMinutes(30), null);
        appointment.setId(APPOINTMENT_ID);
        appointment.prePersist();
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(actorResolver.requireAppointmentParty(EMAIL, PATIENT_ID, DOCTOR_ID)).thenReturn(ACTOR_USER_ID);
        doNothing().when(bookAppointmentService)
                .assertBookableSlot(any(UUID.class), any(Instant.class), any(Instant.class), any(UUID.class));
        when(appointmentRepository.existsOverlappingForPatient(
                PATIENT_ID, com.medops.appointments.domain.AppointmentStatus.BOOKED,
                RESCHEDULED_START, RESCHEDULED_START.plusSeconds(1800), APPOINTMENT_ID))
                .thenReturn(false);
        when(appointmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assembler.toResponse(any())).thenReturn(new AppointmentResponse(
                APPOINTMENT_ID, PATIENT_ID, DOCTOR_ID, "Patient", null, null, null,
                "Doctor", null, RESCHEDULED_START, RESCHEDULED_START.plusSeconds(1800),
                com.medops.appointments.domain.AppointmentStatus.BOOKED, null, null));

        service().reschedule(APPOINTMENT_ID, EMAIL,
                new RescheduleAppointmentRequest(RESCHEDULED_START));

        verify(domainEventPublisher).publishAfterCommit(any(AppointmentRescheduledEvent.class));
        verify(auditService).recordEvent(
                com.medops.shared.audit.AuditEventType.APPOINTMENT_RESCHEDULED, ACTOR_USER_ID, EMAIL);
    }
}
