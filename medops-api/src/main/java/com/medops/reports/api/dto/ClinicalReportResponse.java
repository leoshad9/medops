package com.medops.reports.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.medops.reports.domain.ReportStatus;
import com.medops.reports.infrastructure.ClinicalReport;

public record ClinicalReportResponse(
        UUID id,
        UUID patientId,
        UUID doctorId,
        String patientName,
        String patientMrn,
        String doctorName,
        String title,
        String notes,
        ReportStatus status,
        String originalFilename,
        long sizeBytes,
        boolean hasFile,
        Instant createdAt,
        Instant reviewedAt,
        String summary,
        Instant summarizedAt
) {

    public static ClinicalReportResponse of(
            ClinicalReport report, String patientName, String patientMrn, String doctorName) {
        return of(report, patientName, patientMrn, doctorName, report.getSummary());
    }

    public static ClinicalReportResponse of(
            ClinicalReport report,
            String patientName,
            String patientMrn,
            String doctorName,
            String summaryText) {
        return new ClinicalReportResponse(
                report.getId(),
                report.getPatientProfileId(),
                report.getDoctorProfileId(),
                patientName,
                patientMrn,
                doctorName,
                report.getTitle(),
                report.getNotes(),
                report.getStatus(),
                report.getOriginalFilename(),
                report.getSizeBytes(),
                true,
                report.getCreatedAt(),
                report.getReviewedAt(),
                summaryText,
                report.getSummarizedAt());
    }
}
