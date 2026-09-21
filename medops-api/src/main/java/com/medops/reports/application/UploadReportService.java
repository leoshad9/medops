package com.medops.reports.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.clinical.ClinicalAccessService;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.files.domain.ClinicalFileStorage;
import com.medops.files.domain.PdfUploadPolicy;
import com.medops.files.domain.StoredFile;
import com.medops.files.domain.StorageQuotaExceededException;
import com.medops.files.domain.UploadedPdf;
import com.medops.files.infrastructure.ClinicalFileProperties;
import com.medops.files.infrastructure.UserStorageRepository;
import com.medops.messaging.domain.DomainEventPublisher;
import com.medops.messaging.events.ReportUploadedEvent;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.domain.ReportStatus;
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.reports.infrastructure.ClinicalReportRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadReportService {

    private final ClinicalReportRepository reportRepository;
    private final ClinicalFileStorage fileStorage;
    private final ClinicalAccessService clinicalAccess;
    private final ClinicalReportAssembler assembler;
    private final AuditService auditService;
    private final DomainEventPublisher domainEventPublisher;
    private final PatientProfileRepository patientProfileRepository;
    private final UserStorageRepository userStorageRepository;
    private final ClinicalFileProperties fileProperties;

    @Transactional
    public ClinicalReportResponse upload(
            String doctorEmail, UUID patientId, String title, String notes, UploadedPdf pdf) {
        DoctorProfile doctor = clinicalAccess.requireTreatingDoctor(doctorEmail, patientId);
        PdfUploadPolicy.validate(pdf.contentType(), pdf.content());

        PatientProfile patient = patientProfileRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        userStorageRepository.ensureRecordExists(patient.getUserId());
        if (userStorageRepository.reserveQuota(
                patient.getUserId(), pdf.content().length, fileProperties.storageQuotaBytes()) == 0) {
            throw new StorageQuotaExceededException(fileProperties.storageQuotaBytes());
        }

        StoredFile stored = null;
        try {
            stored = fileStorage.store("reports", pdf.content(), pdf.originalFilename(), pdf.contentType());

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
        } catch (Exception ex) {
            if (stored != null) {
                try {
                    fileStorage.delete(stored.storageKey());
                } catch (Exception deleteEx) {
                    log.warn("Failed to delete orphaned file after upload failure: {}",
                            stored.storageKey(), deleteEx);
                }
            }
            userStorageRepository.releaseQuota(patient.getUserId(), pdf.content().length);
            throw ex;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
