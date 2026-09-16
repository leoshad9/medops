package com.medops.appointments.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.appointments.api.dto.AppointmentPageResponse;
import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.shared.exception.InvalidRequestException;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Read-side appointment queries. Extracted from {@link AppointmentQueryService}
 * so that {@code @Transactional} methods live in their own bean and are always
 * reached through the Spring transactional proxy - never via {@code this.} in a
 * sibling method, which is what Sonar flags as S6809.
 */
@Service
@RequiredArgsConstructor
public class AppointmentQueryReadService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentActorResolver actorResolver;
    private final AppointmentResponseAssembler assembler;

    /** Returns an appointment visible to the authenticated actor. */
    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID appointmentId, String email) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        actorResolver.requireAppointmentParty(
                email, appointment.getPatientProfileId(), appointment.getDoctorProfileId());
        return assembler.toResponse(appointment);
    }

    /**
     * Lists a patient's appointments with an optional status filter.
     *
     * @param email the patient's email
     * @param status optional status filter, may be {@code null}
     * @param page zero-based page index
     * @param size page size
     * @return the requested page of appointments
     */
    @Transactional(readOnly = true)
    public AppointmentPageResponse listForPatient(
            String email, AppointmentStatus status, int page, int size) {
        PatientProfile patient = actorResolver.requirePatient(email);
        Pageable pageable = pageable(page, size);
        Page<Appointment> result = status == null
                ? appointmentRepository.findByPatientProfileIdOrderByStartsAtDesc(patient.getId(), pageable)
                : appointmentRepository.findByPatientProfileIdAndStatusOrderByStartsAtDesc(
                        patient.getId(), status, pageable);
        return toPage(result);
    }

    /**
     * Lists a doctor's appointments with optional status and date-window filters.
     *
     * <p>A date window is all-or-nothing: supplying only one of {@code from}/{@code to}
     * is rejected rather than silently ignored, and a supplied {@code status} always
     * applies - including inside a window.
     *
     * @param email the doctor's email
     * @param status optional status filter, may be {@code null}
     * @param from optional window start (inclusive), must accompany {@code to}
     * @param to optional window end (exclusive), must accompany {@code from}
     * @param page zero-based page index
     * @param size page size
     * @return the requested page of appointments
     */
    @Transactional(readOnly = true)
    public AppointmentPageResponse listForDoctor(
            String email, AppointmentStatus status, Instant from, Instant to, int page, int size) {
        DoctorProfile doctor = actorResolver.requireDoctor(email);
        Pageable pageable = pageable(page, size);
        if (from != null || to != null) {
            if (from == null || to == null) {
                throw new InvalidRequestException("Both from and to must be provided for a date range");
            }
            if (!to.isAfter(from)) {
                throw new InvalidRequestException("The end of the range must be after the start");
            }
            if (status != null) {
                Page<Appointment> window = appointmentRepository
                        .findByDoctorProfileIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                                doctor.getId(), status, from, to, pageable);
                return toPage(window);
            }
            Page<Appointment> window = appointmentRepository
                    .findByDoctorProfileIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                            doctor.getId(), from, to, pageable);
            return toPage(window);
        }
        Page<Appointment> result = status == null
                ? appointmentRepository.findByDoctorProfileIdOrderByStartsAtAsc(doctor.getId(), pageable)
                : appointmentRepository.findByDoctorProfileIdAndStatusOrderByStartsAtAsc(
                        doctor.getId(), status, pageable);
        return toPage(result);
    }

    /** Converts appointments and page metadata into an API response. */
    private AppointmentPageResponse toPage(Page<Appointment> result) {
        return new AppointmentPageResponse(
                result.getContent().stream().map(assembler::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    /** Creates validated pagination settings. */
    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
