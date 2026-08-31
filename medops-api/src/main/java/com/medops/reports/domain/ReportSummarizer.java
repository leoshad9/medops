package com.medops.reports.domain;

/**
 * Plain-language overview of a lab PDF. Implementations must not log PDF bytes or completions.
 */
public interface ReportSummarizer {

    ReportSummary summarize(java.util.UUID reportId, byte[] pdfContent);
}
