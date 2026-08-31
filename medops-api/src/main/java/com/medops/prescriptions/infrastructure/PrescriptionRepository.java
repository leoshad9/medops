package com.medops.prescriptions.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByPatientProfileIdOrderByCreatedAtDesc(UUID patientProfileId);

    List<Prescription> findByDoctorProfileIdOrderByCreatedAtDesc(UUID doctorProfileId);

    List<Prescription> findByDoctorProfileIdAndPatientProfileIdOrderByCreatedAtDesc(
            UUID doctorProfileId, UUID patientProfileId);
}
