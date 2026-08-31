package com.medops.prescriptions.application;

import org.springframework.stereotype.Component;

import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.prescriptions.api.dto.PrescriptionResponse;
import com.medops.prescriptions.infrastructure.Prescription;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PrescriptionAssembler {

    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public PrescriptionResponse toResponse(Prescription prescription) {
        PatientProfile patient = patientProfileRepository.findById(prescription.getPatientProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        DoctorProfile doctor = doctorProfileRepository.findById(prescription.getDoctorProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
        return PrescriptionResponse.of(
                prescription, patient.getFullName(), patient.getMrn(), doctor.getFullName());
    }
}
