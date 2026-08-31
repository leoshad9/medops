package com.medops.doctors.application;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.clinical.CareRelationship;
import com.medops.doctors.api.dto.DoctorPatientSummaryResponse;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorPatientRosterService {

    private final AppointmentActorResolver actorResolver;
    private final CareRelationship careRelationship;
    private final PatientProfileRepository patientProfileRepository;

    @Transactional(readOnly = true)
    public List<DoctorPatientSummaryResponse> listMyPatients(String doctorEmail) {
        DoctorProfile doctor = actorResolver.requireDoctor(doctorEmail);
        List<UUID> patientIds = careRelationship.patientIdsForDoctor(doctor.getId());
        if (patientIds.isEmpty()) {
            return List.of();
        }
        return patientProfileRepository.findAllById(patientIds).stream()
                .sorted(Comparator.comparing(profile -> profile.getFullName().toLowerCase()))
                .map(DoctorPatientSummaryResponse::from)
                .toList();
    }
}
