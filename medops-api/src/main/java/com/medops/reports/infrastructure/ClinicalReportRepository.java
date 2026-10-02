package com.medops.reports.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.medops.reports.domain.ReportStatus;

public interface ClinicalReportRepository extends JpaRepository<ClinicalReport, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ClinicalReport r where r.id = :id")
    Optional<ClinicalReport> findByIdForUpdate(@Param("id") UUID id);

    List<ClinicalReport> findByPatientProfileIdOrderByCreatedAtDesc(UUID patientProfileId);

    List<ClinicalReport> findByDoctorProfileIdOrderByCreatedAtDesc(UUID doctorProfileId);

    List<ClinicalReport> findByDoctorProfileIdAndStatusOrderByCreatedAtDesc(
            UUID doctorProfileId, ReportStatus status);

    List<ClinicalReport> findByDoctorProfileIdAndPatientProfileIdOrderByCreatedAtDesc(
            UUID doctorProfileId, UUID patientProfileId);

    List<ClinicalReport> findByPatientProfileIdAndStatusOrderByCreatedAtDesc(
            UUID patientProfileId, ReportStatus status);
}
