package com.medops.doctors.api.dto;

import java.util.UUID;

import com.medops.doctors.infrastructure.DoctorProfile;

public record DoctorSummaryResponse(
        UUID id,
        String fullName,
        String specialty,
        String licenseNumber
) {

    public static DoctorSummaryResponse from(DoctorProfile profile) {
        return new DoctorSummaryResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getSpecialty(),
                profile.getLicenseNumber());
    }
}
