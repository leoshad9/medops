package com.medops.reports.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.medops.messaging.domain.DomainEventPublisher;
import com.medops.reports.infrastructure.ClinicalReportRepository;
import com.medops.shared.audit.AuditService;

@ExtendWith(MockitoExtension.class)
class UploadReportServiceAuthorizationTest {

    private static final String DOCTOR_EMAIL = "doctor.a@medops.dev";

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

    private UUID foreignPatientId;

    @BeforeEach
    void setUp() {
        foreignPatientId = UUID.randomUUID();
    }

    @Test
    void uploadDeniesDoctorWhenNoCareRelationship() {
        UploadReportService service = new UploadReportService(
                reportRepository, fileStorage, clinicalAccess, assembler, auditService, domainEventPublisher);
        when(clinicalAccess.requireTreatingDoctor(DOCTOR_EMAIL, foreignPatientId))
                .thenThrow(new AccessDeniedException("denied"));
        byte[] pdf = "%PDF-1.4 body".getBytes();

        assertThrows(AccessDeniedException.class, () -> service.upload(
                DOCTOR_EMAIL,
                foreignPatientId,
                "CBC",
                null,
                new UploadedPdf("cbc.pdf", "application/pdf", pdf)));

        verify(fileStorage, never()).store(any(), any(), any(), any());
        verify(reportRepository, never()).save(any());
        verify(domainEventPublisher, never()).publishAfterCommit(any());
    }
}
