package com.medops.reports.application;

import org.springframework.stereotype.Component;

import com.medops.cache.domain.ReportSummaryCache;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClinicalReportAssembler {

    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final ReportSummaryCache summaryCache;

    public ClinicalReportResponse toResponse(ClinicalReport report) {
        PatientProfile patient = patientProfileRepository.findById(report.getPatientProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        DoctorProfile doctor = doctorProfileRepository.findById(report.getDoctorProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found"));
        String summary = summaryCache.get(report.getId()).orElse(report.getSummary());
        return ClinicalReportResponse.of(
                report, patient.getFullName(), patient.getMrn(), doctor.getFullName(), summary);
    }
}
