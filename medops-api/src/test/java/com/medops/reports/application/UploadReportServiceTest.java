package com.medops.reports.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.medops.clinical.ClinicalAccessService;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.files.domain.ClinicalFileStorage;
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

@ExtendWith(MockitoExtension.class)
class UploadReportServiceTest {

    private static final String DOCTOR_EMAIL = "doctor.test@medops.dev";
    private static final String FILE_NAME = "cbc.pdf";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final String REPORT_TITLE = "CBC";
    private static final byte[] PDF_BYTES = "%PDF-1.4 body".getBytes(StandardCharsets.UTF_8);

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

    private UploadReportService service;
    private DoctorProfile doctor;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        service = new UploadReportService(
                reportRepository, fileStorage, clinicalAccess, assembler, auditService, domainEventPublisher);
        patientId = UUID.randomUUID();
        doctor = DoctorProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .fullName("Dr. Test")
                .build();
    }

    @Test
    void uploadPersistsReportWhenDoctorTreatsPatient() {
        when(clinicalAccess.requireTreatingDoctor(DOCTOR_EMAIL, patientId)).thenReturn(doctor);
        when(fileStorage.store(eq("reports"), eq(PDF_BYTES), eq(FILE_NAME), eq(CONTENT_TYPE)))
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
        when(clinicalAccess.requireTreatingDoctor(DOCTOR_EMAIL, patientId))
                .thenThrow(new AccessDeniedException("denied"));
        UploadedPdf uploadedPdf = new UploadedPdf(FILE_NAME, CONTENT_TYPE, PDF_BYTES);

        assertThatThrownBy(() -> service.upload(DOCTOR_EMAIL, patientId, REPORT_TITLE, null, uploadedPdf))
                .isInstanceOf(AccessDeniedException.class);
    }
}
