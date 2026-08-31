package com.medops.reports.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.clinical.ClinicalAccessService;
import com.medops.files.domain.ClinicalFileStorage;
import com.medops.reports.domain.ReportStatus;
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.reports.infrastructure.ClinicalReportRepository;
import com.medops.shared.audit.AuditService;

/**
 * BOLA: swapping report IDs must not leak PDFs across patients or non-treating doctors.
 */
@ExtendWith(MockitoExtension.class)
class ReportQueryServiceAuthorizationTest {

    private static final String PATIENT_A = "patient.a@medops.dev";
    private static final String DOCTOR_A = "doctor.a@medops.dev";

    @Mock
    private ClinicalReportRepository reportRepository;
    @Mock
    private ClinicalFileStorage fileStorage;
    @Mock
    private ClinicalAccessService clinicalAccess;
    @Mock
    private AppointmentActorResolver actorResolver;
    @Mock
    private ClinicalReportAssembler assembler;
    @Mock
    private AuditService auditService;

    private ClinicalReport reportOwnedByPatientB;

    @BeforeEach
    void setUp() {
        reportOwnedByPatientB = ClinicalReport.builder()
                .id(UUID.randomUUID())
                .patientProfileId(UUID.randomUUID())
                .doctorProfileId(UUID.randomUUID())
                .title("CBC")
                .status(ReportStatus.NEW)
                .storageKey("reports/b.pdf")
                .originalFilename("cbc.pdf")
                .contentType("application/pdf")
                .sizeBytes(12)
                .build();
    }

    @Test
    void downloadDeniesPatientWhenReportBelongsToSomeoneElse() {
        ReportQueryService service = new ReportQueryService(reportRepository, fileStorage, clinicalAccess, actorResolver, assembler, auditService, Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        when(reportRepository.findById(reportOwnedByPatientB.getId()))
                .thenReturn(Optional.of(reportOwnedByPatientB));
        doThrow(new AccessDeniedException("denied"))
                .when(clinicalAccess)
                .requireReportReader(PATIENT_A, reportOwnedByPatientB.getPatientProfileId());

        assertThrows(AccessDeniedException.class,
                () -> service.download(reportOwnedByPatientB.getId(), PATIENT_A));

        verify(fileStorage, never()).load(any());
        verify(auditService, never()).recordEvent(any(), any(), any());
    }

    @Test
    void downloadDeniesDoctorWhenNoCareRelationshipToPatient() {
        ReportQueryService service = new ReportQueryService(reportRepository, fileStorage, clinicalAccess, actorResolver, assembler, auditService, Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        when(reportRepository.findById(reportOwnedByPatientB.getId()))
                .thenReturn(Optional.of(reportOwnedByPatientB));
        doThrow(new AccessDeniedException("denied"))
                .when(clinicalAccess)
                .requireReportReader(DOCTOR_A, reportOwnedByPatientB.getPatientProfileId());

        assertThrows(AccessDeniedException.class,
                () -> service.download(reportOwnedByPatientB.getId(), DOCTOR_A));

        verify(fileStorage, never()).load(any());
        verify(auditService, never()).recordEvent(any(), any(), any());
    }

    @Test
    void markReviewedDeniesPatientWhenReportBelongsToSomeoneElse() {
        ReportQueryService service = new ReportQueryService(reportRepository, fileStorage, clinicalAccess, actorResolver, assembler, auditService, Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        when(reportRepository.findById(reportOwnedByPatientB.getId()))
                .thenReturn(Optional.of(reportOwnedByPatientB));
        doThrow(new AccessDeniedException("denied"))
                .when(clinicalAccess)
                .assertPatientOwns(reportOwnedByPatientB.getPatientProfileId(), PATIENT_A);

        assertThrows(AccessDeniedException.class,
                () -> service.markReviewed(reportOwnedByPatientB.getId(), PATIENT_A));
    }
}
