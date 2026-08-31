package com.medops.reports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;

import com.medops.cache.domain.ReportSummaryCache;
import com.medops.clinical.ClinicalAccessService;
import com.medops.files.domain.ClinicalFileStorage;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.domain.ReportStatus;
import com.medops.reports.domain.ReportSummarizer;
import com.medops.reports.domain.ReportSummary;
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.reports.infrastructure.ClinicalReportRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

@ExtendWith(MockitoExtension.class)
class SummarizeReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final String PATIENT_EMAIL = "patient@medops.dev";

    @Mock
    private ClinicalReportRepository reportRepository;
    @Mock
    private ClinicalFileStorage fileStorage;
    @Mock
    private ClinicalAccessService clinicalAccess;
    @Mock
    private ClinicalReportAssembler assembler;
    @Mock
    private ReportSummarizer reportSummarizer;
    @Mock
    private ReportSummaryCache summaryCache;
    @Mock
    private AuditService auditService;

    private SummarizeReportService service;
    private ClinicalReport report;
    private PatientProfile patient;

    @BeforeEach
    void setUp() {
        service = new SummarizeReportService(
                reportRepository,
                fileStorage,
                clinicalAccess,
                assembler,
                reportSummarizer,
                summaryCache,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        patient = PatientProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .mrn("MRN-1")
                .fullName("Pat")
                .build();
        report = ClinicalReport.builder()
                .id(UUID.randomUUID())
                .patientProfileId(patient.getId())
                .doctorProfileId(UUID.randomUUID())
                .title("CBC")
                .status(ReportStatus.NEW)
                .storageKey("reports/a.pdf")
                .originalFilename("cbc.pdf")
                .contentType("application/pdf")
                .sizeBytes(12)
                .build();
    }

    @Test
    void summarizeIfAbsentPersistsAndCachesWhenNoSummary() {
        byte[] pdf = "%PDF-1.4".getBytes();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(fileStorage.load("reports/a.pdf")).thenReturn(new ByteArrayResource(pdf));
        when(reportSummarizer.summarize(report.getId(), pdf))
                .thenReturn(new ReportSummary("Plain overview"));

        service.summarizeIfAbsent(report.getId());

        assertThat(report.getSummary()).isEqualTo("Plain overview");
        assertThat(report.getSummarizedAt()).isEqualTo(NOW);
        verify(summaryCache).put(report.getId(), "Plain overview");
        verify(auditService).recordEvent(AuditEventType.REPORT_SUMMARIZED, null, "system");
    }

    @Test
    void summarizeIfAbsentSkipsWhenAlreadySummarized() {
        report.applySummary("existing", NOW);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        service.summarizeIfAbsent(report.getId());

        verify(reportSummarizer, never()).summarize(any(), any());
        verify(summaryCache, never()).put(any(), any());
    }

    @Test
    void summarizeOnDemandDeniesWhenPatientDoesNotOwn() {
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(clinicalAccess).requireReportReader(PATIENT_EMAIL, report.getPatientProfileId());

        assertThrows(AccessDeniedException.class, () ->
                service.summarizeOnDemand(report.getId(), PATIENT_EMAIL));
    }

    @Test
    void summarizeOnDemandReturnsEnvelopeFields() {
        byte[] pdf = "%PDF-1.4".getBytes();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(clinicalAccess.requireReportReader(PATIENT_EMAIL, report.getPatientProfileId()))
                .thenReturn(patient.getUserId());
        when(fileStorage.load("reports/a.pdf")).thenReturn(new ByteArrayResource(pdf));
        when(reportSummarizer.summarize(report.getId(), pdf))
                .thenReturn(new ReportSummary("Overview text"));
        ClinicalReportResponse mapped = new ClinicalReportResponse(
                report.getId(), patient.getId(), report.getDoctorProfileId(), "Pat", "MRN-1", "Dr",
                "CBC", null, ReportStatus.NEW, "cbc.pdf", 12, true, null, null, "Overview text", NOW);
        when(assembler.toResponse(report)).thenReturn(mapped);

        ClinicalReportResponse result = service.summarizeOnDemand(report.getId(), PATIENT_EMAIL);

        assertThat(result.summary()).isEqualTo("Overview text");
        verify(auditService).recordEvent(AuditEventType.REPORT_SUMMARIZED, patient.getUserId(), PATIENT_EMAIL);
        verify(clinicalAccess).requireReportReader(eq(PATIENT_EMAIL), eq(report.getPatientProfileId()));
    }
}
