package com.medops.reports.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medops.reports.domain.ReportStatus;

public interface ClinicalReportRepository extends JpaRepository<ClinicalReport, UUID> {

    List<ClinicalReport> findByPatientProfileIdOrderByCreatedAtDesc(UUID patientProfileId);

    List<ClinicalReport> findByDoctorProfileIdOrderByCreatedAtDesc(UUID doctorProfileId);

    List<ClinicalReport> findByDoctorProfileIdAndPatientProfileIdOrderByCreatedAtDesc(
            UUID doctorProfileId, UUID patientProfileId);

    List<ClinicalReport> findByPatientProfileIdAndStatusOrderByCreatedAtDesc(
            UUID patientProfileId, ReportStatus status);
}
