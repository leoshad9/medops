package com.medops.reports.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.medops.clinical.ClinicalAccessService;
import com.medops.files.domain.ClinicalFileStorage;
import com.medops.files.domain.UploadedPdf;
import com.medops.files.infrastructure.ClinicalFileProperties;
import com.medops.files.infrastructure.UserStorageRepository;
import com.medops.messaging.domain.DomainEventPublisher;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.reports.infrastructure.ClinicalReportRepository;
import com.medops.shared.audit.AuditService;

@ExtendWith(MockitoExtension.class)
class UploadReportServiceAuthorizationTest {

    private static final String DOCTOR_EMAIL = "doctor.a@medops.dev";
    private static final long QUOTA_BYTES = 50L * 1024 * 1024;

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

    private UUID foreignPatientId;

    @BeforeEach
    void setUp() {
        foreignPatientId = UUID.randomUUID();
    }

    @Test
    void uploadDeniesDoctorWhenNoCareRelationship() {
        UploadReportService service = new UploadReportService(
                reportRepository, fileStorage, clinicalAccess, assembler, auditService,
                domainEventPublisher, patientProfileRepository, userStorageRepository, fileProperties);
        when(clinicalAccess.requireTreatingDoctor(DOCTOR_EMAIL, foreignPatientId))
                .thenThrow(new AccessDeniedException("denied"));
        byte[] pdf = (
                "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n")
                .getBytes();
        UploadedPdf uploadedPdf = new UploadedPdf("cbc.pdf", "application/pdf", pdf);

        AccessDeniedException thrown = assertThrows(AccessDeniedException.class, () -> service.upload(
                DOCTOR_EMAIL,
                foreignPatientId,
                "CBC",
                null,
                uploadedPdf));

        assertEquals("denied", thrown.getMessage());
        verify(fileStorage, never()).store(any(), any(), any(), any());
        verify(reportRepository, never()).save(any());
        verify(domainEventPublisher, never()).publishAfterCommit(any());
    }
}
