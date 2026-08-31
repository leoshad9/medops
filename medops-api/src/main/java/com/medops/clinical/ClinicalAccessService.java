package com.medops.clinical;

import java.util.Objects;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClinicalAccessService {

    private final CareRelationship careRelationship;
    private final AppointmentActorResolver actorResolver;
    private final PatientProfileRepository patientProfileRepository;

    public PatientProfile requirePatientProfile(UUID patientId) {
        return patientProfileRepository.findById(Objects.requireNonNull(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
    }

    public DoctorProfile requireTreatingDoctor(String doctorEmail, UUID patientId) {
        DoctorProfile doctor = actorResolver.requireDoctor(doctorEmail);
        requirePatientProfile(patientId);
        if (!careRelationship.doctorMayTreat(doctor.getId(), patientId)) {
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
        return doctor;
    }

    public void assertPatientOwns(UUID patientProfileId, String patientEmail) {
        PatientProfile patient = actorResolver.requirePatient(patientEmail);
        if (!patient.getId().equals(patientProfileId)) {
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
    }

    public void assertDoctorOwns(UUID doctorProfileId, String doctorEmail) {
        DoctorProfile doctor = actorResolver.requireDoctor(doctorEmail);
        if (!doctor.getId().equals(doctorProfileId)) {
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
    }

    /**
     * Treating doctor, or the owning patient. A doctor without a care relationship
     * is denied rather than falling through to a patient-ownership check.
     *
     * @return the actor's user id for audit
     */
    public UUID requireReportReader(String email, UUID patientProfileId) {
        if (actorResolver.findDoctor(email).isPresent()) {
            return requireTreatingDoctor(email, patientProfileId).getUserId();
        }
        assertPatientOwns(patientProfileId, email);
        return actorResolver.requirePatient(email).getUserId();
    }
}
