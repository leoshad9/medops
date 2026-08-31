package com.medops.appointments.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.api.dto.BookAppointmentRequest;
import com.medops.appointments.domain.AppointmentSlotPolicy;
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
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookAppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final AppointmentActorResolver actorResolver;
    private final AppointmentResponseAssembler assembler;
    private final AppointmentScheduleProperties schedule;
    private final AuditService auditService;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Instant> listAvailableSlots(UUID doctorId, LocalDate date) {
        requireDoctor(doctorId);
        Instant now = clock.instant();
        Instant dayStart = date.atStartOfDay(zone()).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone()).toInstant();

        List<Instant> booked = appointmentRepository
                .findByDoctorProfileIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                        doctorId, AppointmentStatus.BOOKED, dayStart, dayEnd)
                .stream()
                .map(Appointment::getStartsAt)
                .toList();

        return AppointmentSlotPolicy.generateSlots(
                        date, zone(), schedule.slotLength(), schedule.open(), schedule.close())
                .stream()
                .filter(slot -> slot.isAfter(now))
                .filter(slot -> !booked.contains(slot))
                .toList();
    }

    @Transactional
    public AppointmentResponse book(String patientEmail, BookAppointmentRequest request) {
        PatientProfile patient = actorResolver.requirePatient(patientEmail);
        DoctorProfile doctor = requireDoctor(request.doctorId());
        Instant now = clock.instant();
        Instant startsAt = request.startsAt();

        assertBookableSlot(doctor.getId(), startsAt, now, null);

        if (appointmentRepository.existsOverlappingForPatient(
                patient.getId(), AppointmentStatus.BOOKED, startsAt, startsAt.plus(schedule.slotLength()), null)) {
            throw new ConflictException("You already have an appointment overlapping that time");
        }

        Appointment saved;
        try {
            saved = appointmentRepository.saveAndFlush(Appointment.book(
                    patient.getId(),
                    doctor.getId(),
                    startsAt,
                    schedule.slotLength(),
                    blankToNull(request.reason())));
        } catch (DataIntegrityViolationException ex) {
            // Concurrent booking of the same doctor slot loses the unique index race.
            throw new ConflictException("That time is no longer available", ex);
        }

        auditService.recordEvent(AuditEventType.APPOINTMENT_BOOKED, patient.getUserId(), patientEmail);
        domainEventPublisher.publishAfterCommit(AppointmentBookedEvent.of(saved.getId()));
        return assembler.toResponse(saved);
    }

    void assertBookableSlot(UUID doctorId, Instant startsAt, Instant now, UUID excludeAppointmentId) {
        if (!startsAt.isAfter(now)) {
            throw new InvalidRequestException("Appointments must be booked in the future");
        }
        if (!AppointmentSlotPolicy.isOnGrid(
                startsAt, zone(), schedule.slotLength(), schedule.open(), schedule.close())) {
            throw new InvalidRequestException("That time is not a valid clinic slot");
        }
        appointmentRepository.findByDoctorProfileIdAndStatusAndStartsAt(
                        doctorId, AppointmentStatus.BOOKED, startsAt)
                .filter(existing -> excludeAppointmentId == null || !existing.getId().equals(excludeAppointmentId))
                .ifPresent(existing -> {
                    throw new ConflictException("That time is no longer available");
                });
    }

    private DoctorProfile requireDoctor(UUID doctorId) {
        return doctorProfileRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
    }

    private ZoneId zone() {
        return schedule.zoneId();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
