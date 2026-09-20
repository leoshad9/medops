package com.medops.appointments.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.medops.appointments.domain.AppointmentStatus;
import com.medops.patients.domain.Gender;
import com.medops.appointments.infrastructure.Appointment;

public record AppointmentResponse(
        UUID id,
        UUID patientId,
        UUID doctorId,
        String patientName,
        String patientMrn,
        LocalDate patientDateOfBirth,
        Gender patientGender,
        String doctorName,
        String specialty,
        Instant startsAt,
        Instant endsAt,
        AppointmentStatus status,
        String reason,
        String location
) {

    public static AppointmentResponse of(
            Appointment appointment,
            String patientName,
            String patientMrn,
            LocalDate patientDateOfBirth,
            Gender patientGender,
            String doctorName,
            String specialty) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPatientProfileId(),
                appointment.getDoctorProfileId(),
                patientName,
                patientMrn,
                patientDateOfBirth,
                patientGender,
                doctorName,
                specialty,
                appointment.getStartsAt(),
                appointment.getEndsAt(),
                appointment.getStatus(),
                appointment.getReason(),
                appointment.getLocation());
    }
}
