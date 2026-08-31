package com.medops.appointments.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.medops.auth.entity.User;
import com.medops.auth.entity.UserStatus;
import com.medops.auth.repository.UserRepository;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppointmentActorResolver {

    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public PatientProfile requirePatient(String email) {
        User user = requireActiveUser(email);
        return patientProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("A patient profile is required"));
    }

    public DoctorProfile requireDoctor(String email) {
        return findDoctor(email)
                .orElseThrow(() -> new AccessDeniedException("A doctor profile is required"));
    }

    public Optional<DoctorProfile> findDoctor(String email) {
        User user = requireActiveUser(email);
        return doctorProfileRepository.findByUserId(user.getId());
    }

    /**
     * The assigned doctor, or the owning patient. A doctor who is not on this
     * appointment is denied rather than falling through to a patient profile.
     *
     * @return the actor's user id for audit
     */
    public UUID requireAppointmentParty(String email, UUID patientProfileId, UUID doctorProfileId) {
        Optional<DoctorProfile> doctor = findDoctor(email);
        if (doctor.isPresent()) {
            if (!doctor.get().getId().equals(doctorProfileId)) {
                throw new AccessDeniedException("You do not have permission to perform this action");
            }
            return doctor.get().getUserId();
        }
        PatientProfile patient = requirePatient(email);
        if (!patient.getId().equals(patientProfileId)) {
            throw new AccessDeniedException("You do not have permission to perform this action");
        }
        return patient.getUserId();
    }

    public User requireActiveUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("Account is not active");
        }
        return user;
    }
}
