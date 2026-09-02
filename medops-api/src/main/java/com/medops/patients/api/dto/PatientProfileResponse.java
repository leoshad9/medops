package com.medops.patients.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.medops.patients.domain.Gender;
import com.medops.patients.infrastructure.PatientProfile;

public record PatientProfileResponse(
        UUID id,
        String email,
        String fullName,
        String mrn,
        LocalDate dateOfBirth,
        Gender gender,
        String phoneNumber
) {

    public static PatientProfileResponse of(PatientProfile profile, String email) {
        return new PatientProfileResponse(
                profile.getId(),
                email,
                profile.getFullName(),
                profile.getMrn(),
                profile.getDateOfBirth(),
                profile.getGender(),
                profile.getPhoneNumber());
    }
}
