package com.medops.appointments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.api.dto.BookAppointmentRequest;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.appointments.infrastructure.AppointmentScheduleProperties;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.messaging.domain.DomainEventPublisher;
import com.medops.messaging.events.AppointmentBookedEvent;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ConflictException;
import com.medops.shared.exception.InvalidRequestException;

@ExtendWith(MockitoExtension.class)
class BookAppointmentServiceTest {

    private static final String PATIENT_EMAIL = "patient@medops.dev";
    private static final Instant NOW = Instant.parse("2026-08-31T03:00:00Z");

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private AppointmentActorResolver actorResolver;
    @Mock
    private AppointmentResponseAssembler assembler;
    @Mock
    private AuditService auditService;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private BookAppointmentService service;
    private PatientProfile patient;
    private DoctorProfile doctor;

    @BeforeEach
    void setUp() {
        AppointmentScheduleProperties schedule = new AppointmentScheduleProperties(
                "Asia/Kolkata", 30, LocalTime.of(9, 0), LocalTime.of(17, 0));
        service = new BookAppointmentService(
                appointmentRepository,
                doctorProfileRepository,
                actorResolver,
                assembler,
                schedule,
                auditService,
                domainEventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        patient = PatientProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .mrn("MRN-1")
                .fullName("Test Patient")
                .build();
        doctor = DoctorProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .fullName("Dr. Khan")
                .specialty("Cardiology")
                .licenseNumber("LIC-1")
                .phoneNumber("+919000000000")
                .build();
    }

    @Test
    void bookPersistsAndAudits() {
        Instant start = LocalDate.of(2026, 8, 31).atTime(10, 0)
                .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
        when(actorResolver.requirePatient(PATIENT_EMAIL)).thenReturn(patient);
        when(doctorProfileRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorProfileIdAndStatusAndStartsAt(
                doctor.getId(), AppointmentStatus.BOOKED, start)).thenReturn(Optional.empty());
        when(appointmentRepository.existsOverlappingForPatient(
                eq(patient.getId()), eq(AppointmentStatus.BOOKED), eq(start), any(), isNull()))
                .thenReturn(false);
        when(appointmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AppointmentResponse mapped = new AppointmentResponse(
                UUID.randomUUID(), patient.getId(), doctor.getId(),
                patient.getFullName(), patient.getMrn(), doctor.getFullName(), doctor.getSpecialty(),
                start, start.plusSeconds(1800), AppointmentStatus.BOOKED, "chest pain", null);
        when(assembler.toResponse(any())).thenReturn(mapped);

        AppointmentResponse response = service.book(
                PATIENT_EMAIL, new BookAppointmentRequest(doctor.getId(), start, "chest pain"));

        assertThat(response.doctorName()).isEqualTo("Dr. Khan");
        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        verify(auditService).recordEvent(AuditEventType.APPOINTMENT_BOOKED, patient.getUserId(), PATIENT_EMAIL);
        verify(domainEventPublisher).publishAfterCommit(any(AppointmentBookedEvent.class));
    }

    @Test
    void bookMapsUniqueConstraintRaceToConflict() {
        Instant start = LocalDate.of(2026, 8, 31).atTime(10, 0)
                .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
        when(actorResolver.requirePatient(PATIENT_EMAIL)).thenReturn(patient);
        when(doctorProfileRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorProfileIdAndStatusAndStartsAt(
                doctor.getId(), AppointmentStatus.BOOKED, start)).thenReturn(Optional.empty());
        when(appointmentRepository.existsOverlappingForPatient(
                eq(patient.getId()), eq(AppointmentStatus.BOOKED), eq(start), any(), isNull()))
                .thenReturn(false);
        when(appointmentRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq_appointments_doctor_booked_slot"));

        assertThrows(ConflictException.class, () -> service.book(
                PATIENT_EMAIL, new BookAppointmentRequest(doctor.getId(), start, "chest pain")));
    }

    @Test
    void bookRejectsOffGridTime() {
        Instant start = Instant.parse("2026-08-31T04:05:00Z");
        when(actorResolver.requirePatient(PATIENT_EMAIL)).thenReturn(patient);
        when(doctorProfileRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        assertThrows(InvalidRequestException.class, () -> service.book(
                PATIENT_EMAIL, new BookAppointmentRequest(doctor.getId(), start, null)));
    }

    @Test
    void bookRejectsTakenSlot() {
        Instant start = LocalDate.of(2026, 8, 31).atTime(10, 0)
                .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
        when(actorResolver.requirePatient(PATIENT_EMAIL)).thenReturn(patient);
        when(doctorProfileRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        Appointment existing = Appointment.book(UUID.randomUUID(), doctor.getId(), start, java.time.Duration.ofMinutes(30), null);
        existing.prePersist();
        when(appointmentRepository.findByDoctorProfileIdAndStatusAndStartsAt(
                doctor.getId(), AppointmentStatus.BOOKED, start)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> service.book(
                PATIENT_EMAIL, new BookAppointmentRequest(doctor.getId(), start, null)));
    }

    @Test
    void listAvailableSlotsOmitsBookedAndPast() {
        Instant bookedStart = LocalDate.of(2026, 8, 31).atTime(10, 0)
                .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
        when(doctorProfileRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorProfileIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                eq(doctor.getId()), eq(AppointmentStatus.BOOKED), any(), any()))
                .thenReturn(List.of(Appointment.book(patient.getId(), doctor.getId(), bookedStart,
                        java.time.Duration.ofMinutes(30), null)));

        List<Instant> slots = service.listAvailableSlots(doctor.getId(), LocalDate.of(2026, 8, 31));

        assertThat(slots).isNotEmpty();
        assertThat(slots).doesNotContain(bookedStart);
        assertThat(slots).allMatch(slot -> slot.isAfter(NOW));
    }
}
