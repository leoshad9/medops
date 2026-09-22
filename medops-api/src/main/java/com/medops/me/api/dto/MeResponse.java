package com.medops.me.api.dto;

import java.util.UUID;

import com.medops.doctors.api.dto.DoctorProfileResponse;
import com.medops.patients.api.dto.PatientProfileResponse;

/**
 * Single-request session bootstrap for the SPA: the identity carried by the
 * authenticated principal plus the profile of the caller's role. {@code patientProfile}
 * and {@code doctorProfile} are mutually exclusive and are {@code null} only when the
 * role-specific lookup failed (see {@code MeBootstrapService}).
 *
 * @param id             the user's identifier
 * @param email          the authenticated email
 * @param role           primary role name (DOCTOR or PATIENT)
 * @param patientProfile the patient profile for PATIENT callers, otherwise null
 * @param doctorProfile  the doctor profile for DOCTOR callers, otherwise null
 */
public record MeResponse(
        UUID id,
        String email,
        String role,
        PatientProfileResponse patientProfile,
        DoctorProfileResponse doctorProfile
) {
}
