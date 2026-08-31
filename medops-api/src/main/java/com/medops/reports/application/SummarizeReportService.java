package com.medops.reports.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.cache.domain.ReportSummaryCache;
import com.medops.clinical.ClinicalAccessService;
import com.medops.files.domain.ClinicalFileStorage;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.domain.ReportSummarizer;
import com.medops.reports.domain.ReportSummary;
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.reports.infrastructure.ClinicalReportRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SummarizeReportService {

    private final ClinicalReportRepository reportRepository;
    private final ClinicalFileStorage fileStorage;
    private final ClinicalAccessService clinicalAccess;
    private final ClinicalReportAssembler assembler;
    private final ReportSummarizer reportSummarizer;
    private final ReportSummaryCache summaryCache;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Kafka / background path: summarize once; skip when a summary already exists.
     */
    @Transactional
    public void summarizeIfAbsent(UUID reportId) {
        ClinicalReport report = reportRepository.findById(reportId).orElse(null);
        if (report == null || report.hasSummary()) {
            return;
        }
        applySummary(report);
        auditService.recordEvent(AuditEventType.REPORT_SUMMARIZED, null, "system");
    }

    /**
     * On-demand path for patient owner or treating doctor (may refresh an existing summary).
     */
    @Transactional
    public ClinicalReportResponse summarizeOnDemand(UUID reportId, String email) {
        ClinicalReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        UUID actorUserId = clinicalAccess.requireReportReader(email, report.getPatientProfileId());
        applySummary(report);
        auditService.recordEvent(AuditEventType.REPORT_SUMMARIZED, actorUserId, email);
        return assembler.toResponse(report);
    }

    private void applySummary(ClinicalReport report) {
        byte[] pdf = readPdf(report.getStorageKey());
        ReportSummary summary = reportSummarizer.summarize(report.getId(), pdf);
        report.applySummary(summary.text(), clock.instant());
        summaryCache.put(report.getId(), summary.text());
    }

    private byte[] readPdf(String storageKey) {
        Resource resource = fileStorage.load(storageKey);
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to read clinical report PDF", ex);
        }
    }
}
