package com.medops.doctors.api.dto;

import java.util.UUID;

import com.medops.patients.infrastructure.PatientProfile;

public record DoctorPatientSummaryResponse(
        UUID id,
        String fullName,
        String mrn
) {

    public static DoctorPatientSummaryResponse from(PatientProfile profile) {
        return new DoctorPatientSummaryResponse(profile.getId(), profile.getFullName(), profile.getMrn());
    }
}
