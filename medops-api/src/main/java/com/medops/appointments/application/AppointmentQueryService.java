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

@Service
@RequiredArgsConstructor
public class AppointmentQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentActorResolver actorResolver;
    private final AppointmentResponseAssembler assembler;

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID appointmentId, String email) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
        actorResolver.requireAppointmentParty(
                email, appointment.getPatientProfileId(), appointment.getDoctorProfileId());
        return assembler.toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public AppointmentPageResponse list(
            String email, AppointmentStatus status, Instant from, Instant to, int page, int size) {
        if (actorResolver.findDoctor(email).isPresent()) {
            return listForDoctor(email, status, from, to, page, size);
        }
        return listForPatient(email, status, page, size);
    }

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

    @Transactional(readOnly = true)
    public AppointmentPageResponse listForDoctor(
            String email, AppointmentStatus status, Instant from, Instant to, int page, int size) {
        DoctorProfile doctor = actorResolver.requireDoctor(email);
        Pageable pageable = pageable(page, size);
        if (from != null && to != null) {
            if (!to.isAfter(from)) {
                throw new InvalidRequestException("The end of the range must be after the start");
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

    private AppointmentPageResponse toPage(Page<Appointment> result) {
        return new AppointmentPageResponse(
                result.getContent().stream().map(assembler::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
