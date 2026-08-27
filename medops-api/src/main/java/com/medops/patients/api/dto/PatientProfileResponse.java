package com.medops.patients.api.dto;

import java.time.LocalDate;

import com.medops.patients.domain.Gender;
import com.medops.patients.infrastructure.PatientProfile;

public record PatientProfileResponse(
        String email,
        String fullName,
        String mrn,
        LocalDate dateOfBirth,
        Gender gender,
        String phoneNumber
) {

    public static PatientProfileResponse of(PatientProfile profile, String email) {
        return new PatientProfileResponse(
                email,
                profile.getFullName(),
                profile.getMrn(),
                profile.getDateOfBirth(),
                profile.getGender(),
                profile.getPhoneNumber());
    }
}
