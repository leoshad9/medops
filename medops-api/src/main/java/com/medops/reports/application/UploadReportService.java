package com.medops.reports.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.clinical.ClinicalAccessService;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.files.domain.ClinicalFileStorage;
import com.medops.files.domain.PdfUploadPolicy;
import com.medops.files.domain.StoredFile;
import com.medops.files.domain.UploadedPdf;
import com.medops.messaging.domain.DomainEventPublisher;
import com.medops.messaging.events.ReportUploadedEvent;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.domain.ReportStatus;
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.reports.infrastructure.ClinicalReportRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UploadReportService {

    private final ClinicalReportRepository reportRepository;
    private final ClinicalFileStorage fileStorage;
    private final ClinicalAccessService clinicalAccess;
    private final ClinicalReportAssembler assembler;
    private final AuditService auditService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public ClinicalReportResponse upload(
            String doctorEmail, UUID patientId, String title, String notes, UploadedPdf pdf) {
        DoctorProfile doctor = clinicalAccess.requireTreatingDoctor(doctorEmail, patientId);
        PdfUploadPolicy.validate(pdf.contentType(), pdf.content());
        StoredFile stored = fileStorage.store("reports", pdf.content(), pdf.originalFilename(), pdf.contentType());

        ClinicalReport saved = reportRepository.save(ClinicalReport.builder()
                .patientProfileId(patientId)
                .doctorProfileId(doctor.getId())
                .title(title.trim())
                .notes(blankToNull(notes))
                .status(ReportStatus.NEW)
                .storageKey(stored.storageKey())
                .originalFilename(stored.originalFilename())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .build());

        auditService.recordEvent(AuditEventType.REPORT_UPLOADED, doctor.getUserId(), doctorEmail);
        domainEventPublisher.publishAfterCommit(ReportUploadedEvent.of(saved.getId()));
        return assembler.toResponse(saved);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
