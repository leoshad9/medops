package com.medops.appointments.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.clinical.CareRelationship;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppointmentCareRelationship implements CareRelationship {

    private static final Set<AppointmentStatus> TREATING_STATUSES =
            Set.of(AppointmentStatus.BOOKED, AppointmentStatus.COMPLETED);

    private final AppointmentRepository appointmentRepository;

    @Override
    public boolean doctorMayTreat(UUID doctorProfileId, UUID patientProfileId) {
        return appointmentRepository.existsByDoctorProfileIdAndPatientProfileIdAndStatusIn(
                doctorProfileId, patientProfileId, TREATING_STATUSES);
    }

    @Override
    public List<UUID> patientIdsForDoctor(UUID doctorProfileId) {
        return appointmentRepository.findDistinctPatientProfileIdsByDoctorProfileIdAndStatusIn(
                doctorProfileId, TREATING_STATUSES);
    }
}
