package com.medops.reports.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.medops.messaging.infrastructure.DomainEventMessage;
import com.medops.reports.application.SummarizeReportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "medops.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReportUploadedListener {

    private final SummarizeReportService summarizeReportService;

    @KafkaListener(
            topics = "${medops.messaging.reports-topic}",
            groupId = "medops-api-reports",
            containerFactory = "domainEventKafkaListenerContainerFactory")
    public void onReportUploaded(DomainEventMessage message) {
        if (message == null || !"ReportUploaded".equals(message.eventType()) || message.reportId() == null) {
            return;
        }
        log.info("Consuming ReportUploaded reportId={}", message.reportId());
        summarizeReportService.summarizeIfAbsent(message.reportId());
    }
}
