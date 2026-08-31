package com.medops.appointments.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.api.dto.RescheduleAppointmentRequest;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.appointments.infrastructure.AppointmentScheduleProperties;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ConflictException;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppointmentTransitionService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentActorResolver actorResolver;
    private final AppointmentResponseAssembler assembler;
    private final BookAppointmentService bookAppointmentService;
    private final AppointmentScheduleProperties schedule;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public AppointmentResponse cancel(UUID appointmentId, String email) {
        VisibleAppointment visible = requireVisible(appointmentId, email);
        Instant now = clock.instant();
        visible.appointment().cancel(now);
        auditService.recordEvent(AuditEventType.APPOINTMENT_CANCELLED, visible.actorUserId(), email);
        return assembler.toResponse(appointmentRepository.save(visible.appointment()));
    }

    @Transactional
    public AppointmentResponse reschedule(
            UUID appointmentId, String email, RescheduleAppointmentRequest request) {
        VisibleAppointment visible = requireVisible(appointmentId, email);
        Appointment appointment = visible.appointment();
        Instant now = clock.instant();
        Instant newStart = request.startsAt();
        bookAppointmentService.assertBookableSlot(appointment.getDoctorProfileId(), newStart, now, appointment.getId());

        if (appointmentRepository.existsOverlappingForPatient(
                appointment.getPatientProfileId(),
                AppointmentStatus.BOOKED,
                newStart,
                newStart.plus(schedule.slotLength()),
                appointment.getId())) {
            throw new ConflictException("You already have an appointment overlapping that time");
        }

        appointment.reschedule(newStart, schedule.slotLength(), now);
        auditService.recordEvent(AuditEventType.APPOINTMENT_RESCHEDULED, visible.actorUserId(), email);
        try {
            return assembler.toResponse(appointmentRepository.saveAndFlush(appointment));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("That time is no longer available", ex);
        }
    }

    @Transactional
    public AppointmentResponse complete(UUID appointmentId, String doctorEmail) {
        DoctorProfile doctor = actorResolver.requireDoctor(doctorEmail);
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        if (!appointment.getDoctorProfileId().equals(doctor.getId())) {
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
        appointment.complete(clock.instant());
        auditService.recordEvent(AuditEventType.APPOINTMENT_COMPLETED, doctor.getUserId(), doctorEmail);
        return assembler.toResponse(appointmentRepository.save(appointment));
    }

    private VisibleAppointment requireVisible(UUID appointmentId, String email) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        UUID actorUserId = actorResolver.requireAppointmentParty(
                email, appointment.getPatientProfileId(), appointment.getDoctorProfileId());
        return new VisibleAppointment(appointment, actorUserId);
    }

    private record VisibleAppointment(Appointment appointment, UUID actorUserId) {
    }
}
