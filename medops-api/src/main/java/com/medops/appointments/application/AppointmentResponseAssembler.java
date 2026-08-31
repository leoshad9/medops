package com.medops.appointments.application;

import org.springframework.stereotype.Component;

import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.infrastructure.Appointment;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppointmentResponseAssembler {

    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public AppointmentResponse toResponse(Appointment appointment) {
        PatientProfile patient = patientProfileRepository.findById(appointment.getPatientProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        DoctorProfile doctor = doctorProfileRepository.findById(appointment.getDoctorProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
        return AppointmentResponse.of(
                appointment,
                patient.getFullName(),
                patient.getMrn(),
                doctor.getFullName(),
                doctor.getSpecialty());
    }
}
