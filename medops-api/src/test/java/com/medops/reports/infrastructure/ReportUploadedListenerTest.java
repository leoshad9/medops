package com.medops.reports.infrastructure;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medops.messaging.infrastructure.DomainEventMessage;
import com.medops.reports.application.SummarizeReportService;

@ExtendWith(MockitoExtension.class)
class ReportUploadedListenerTest {

    @Mock
    private SummarizeReportService summarizeReportService;

    @Test
    void onReportUploadedInvokesSummarize() {
        UUID reportId = UUID.randomUUID();
        ReportUploadedListener listener = new ReportUploadedListener(summarizeReportService);
        listener.onReportUploaded(DomainEventMessage.reportUploaded(reportId, Instant.now()));
        verify(summarizeReportService).summarizeIfAbsent(reportId);
    }

    @Test
    void onReportUploadedIgnoresOtherEvents() {
        ReportUploadedListener listener = new ReportUploadedListener(summarizeReportService);
        listener.onReportUploaded(DomainEventMessage.appointmentBooked(UUID.randomUUID(), Instant.now()));
        verify(summarizeReportService, never()).summarizeIfAbsent(org.mockito.ArgumentMatchers.any());
    }
}
