package com.medops.doctors.api.dto;

import com.medops.doctors.infrastructure.DoctorProfile;

public record DoctorProfileResponse(
        String email,
        String fullName,
        String specialty,
        String licenseNumber,
        String phoneNumber
) {

    public static DoctorProfileResponse of(DoctorProfile profile, String email) {
        return new DoctorProfileResponse(
                email,
                profile.getFullName(),
                profile.getSpecialty(),
                profile.getLicenseNumber(),
                profile.getPhoneNumber());
    }
}
