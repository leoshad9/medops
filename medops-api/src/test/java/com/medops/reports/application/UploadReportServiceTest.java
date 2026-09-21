package com.medops.reports.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.medops.clinical.ClinicalAccessService;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.files.domain.ClinicalFileStorage;
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

import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class UploadReportServiceTest {

    private static final String DOCTOR_EMAIL = "doctor.test@medops.dev";
    private static final String FILE_NAME = "cbc.pdf";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final String REPORT_TITLE = "CBC";
    private static final long QUOTA_BYTES = 50L * 1024 * 1024;
    private static final byte[] PDF_BYTES = (
            "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n")
            .getBytes(StandardCharsets.UTF_8);

    @Mock
    private ClinicalReportRepository reportRepository;
    @Mock
    private ClinicalFileStorage fileStorage;
    @Mock
    private ClinicalAccessService clinicalAccess;
    @Mock
    private ClinicalReportAssembler assembler;
    @Mock
    private AuditService auditService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private UserStorageRepository userStorageRepository;

    private final ClinicalFileProperties fileProperties =
            new ClinicalFileProperties("./data/clinical-files", QUOTA_BYTES);

    private UploadReportService service;
    private DoctorProfile doctor;
    private PatientProfile patient;
    private UUID patientId;

    private void setupCommonMocks() {
        service = new UploadReportService(
                reportRepository, fileStorage, clinicalAccess, assembler, auditService,
                domainEventPublisher, patientProfileRepository, userStorageRepository, fileProperties);
        patientId = UUID.randomUUID();
        doctor = DoctorProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .fullName("Dr. Test")
                .build();
        patient = PatientProfile.builder()
                .id(patientId)
                .userId(UUID.randomUUID())
                .fullName("Test Patient")
                .build();

        when(clinicalAccess.requireTreatingDoctor(DOCTOR_EMAIL, patientId)).thenReturn(doctor);
        when(patientProfileRepository.findById(patientId)).thenReturn(java.util.Optional.of(patient));
        when(userStorageRepository.reserveQuota(patient.getUserId(), PDF_BYTES.length, QUOTA_BYTES))
                .thenReturn(1);
    }

    @Test
    void uploadPersistsReportWhenDoctorTreatsPatient() {
        setupCommonMocks();

        when(fileStorage.store("reports", PDF_BYTES, FILE_NAME, CONTENT_TYPE))
                .thenReturn(new StoredFile("reports/a.pdf", FILE_NAME, CONTENT_TYPE, PDF_BYTES.length));
        when(reportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ClinicalReportResponse mapped = new ClinicalReportResponse(
                UUID.randomUUID(), patientId, doctor.getId(), "Pat", "MRN-1", "Dr. Test",
                REPORT_TITLE, null, ReportStatus.NEW, FILE_NAME, PDF_BYTES.length, true, null, null, null, null);
        when(assembler.toResponse(any())).thenReturn(mapped);

        ClinicalReportResponse result = service.upload(
                DOCTOR_EMAIL, patientId, REPORT_TITLE, null, new UploadedPdf(FILE_NAME, CONTENT_TYPE, PDF_BYTES));

        assertThat(result.title()).isEqualTo(REPORT_TITLE);
        ArgumentCaptor<ClinicalReport> captor = ArgumentCaptor.forClass(ClinicalReport.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.NEW);
        verify(auditService).recordEvent(AuditEventType.REPORT_UPLOADED, doctor.getUserId(), DOCTOR_EMAIL);
        verify(domainEventPublisher).publishAfterCommit(any(ReportUploadedEvent.class));
    }

    @Test
    void uploadDeniesWhenNoCareRelationship() {
        service = new UploadReportService(
                reportRepository, fileStorage, clinicalAccess, assembler, auditService,
                domainEventPublisher, patientProfileRepository, userStorageRepository, fileProperties);
        patientId = UUID.randomUUID();
        doctor = DoctorProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .fullName("Dr. Test")
                .build();

        when(clinicalAccess.requireTreatingDoctor(DOCTOR_EMAIL, patientId))
                .thenThrow(new AccessDeniedException("denied"));
        UploadedPdf uploadedPdf = new UploadedPdf(FILE_NAME, CONTENT_TYPE, PDF_BYTES);

        assertThatThrownBy(() -> service.upload(DOCTOR_EMAIL, patientId, REPORT_TITLE, null, uploadedPdf))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void uploadRejectsWhenQuotaExceeded() {
        setupCommonMocks();
        when(userStorageRepository.reserveQuota(patient.getUserId(), PDF_BYTES.length, QUOTA_BYTES))
                .thenReturn(0);
        UploadedPdf uploadedPdf = new UploadedPdf(FILE_NAME, CONTENT_TYPE, PDF_BYTES);

        assertThatThrownBy(() -> service.upload(DOCTOR_EMAIL, patientId, REPORT_TITLE, null, uploadedPdf))
                .isInstanceOf(StorageQuotaExceededException.class);

        verify(userStorageRepository).ensureRecordExists(patient.getUserId());
        verify(userStorageRepository).reserveQuota(patient.getUserId(), PDF_BYTES.length, QUOTA_BYTES);
        verify(fileStorage, never()).store(any(), any(), any(), any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void uploadDeletesFileWhenDatabaseSaveFails() {
        setupCommonMocks();
        StoredFile stored = new StoredFile("reports/orphan.pdf", FILE_NAME, CONTENT_TYPE, PDF_BYTES.length);
        when(fileStorage.store("reports", PDF_BYTES, FILE_NAME, CONTENT_TYPE)).thenReturn(stored);
        when(reportRepository.save(any())).thenThrow(new RuntimeException("DB failure"));

        assertThatThrownBy(() -> service.upload(
                DOCTOR_EMAIL, patientId, REPORT_TITLE, null,
                new UploadedPdf(FILE_NAME, CONTENT_TYPE, PDF_BYTES)))
                .isInstanceOf(RuntimeException.class);

        verify(fileStorage).store("reports", PDF_BYTES, FILE_NAME, CONTENT_TYPE);
        verify(fileStorage).delete("reports/orphan.pdf");
        verify(reportRepository).save(any());
        verify(userStorageRepository).releaseQuota(patient.getUserId(), PDF_BYTES.length);
    }
}
