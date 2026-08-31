package com.medops.prescriptions.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.medops.prescriptions.domain.PrescriptionStatus;
import com.medops.prescriptions.infrastructure.Prescription;

public record PrescriptionResponse(
        UUID id,
        UUID patientId,
        UUID doctorId,
        String patientName,
        String patientMrn,
        String doctorName,
        String medicationName,
        String dosage,
        String instructions,
        int refillsRemaining,
        PrescriptionStatus status,
        String originalFilename,
        boolean hasFile,
        Instant createdAt
) {

    public static PrescriptionResponse of(
            Prescription prescription, String patientName, String patientMrn, String doctorName) {
        return new PrescriptionResponse(
                prescription.getId(),
                prescription.getPatientProfileId(),
                prescription.getDoctorProfileId(),
                patientName,
                patientMrn,
                doctorName,
                prescription.getMedicationName(),
                prescription.getDosage(),
                prescription.getInstructions(),
                prescription.getRefillsRemaining(),
                prescription.getStatus(),
                prescription.getOriginalFilename(),
                prescription.getStorageKey() != null,
                prescription.getCreatedAt());
    }
}
