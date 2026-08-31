package com.medops.reports.infrastructure;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.medops.reports.domain.ReportStatus;
import com.medops.shared.exception.InvalidRequestException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "clinical_reports", schema = "reports", indexes = {
        @Index(name = "idx_reports_patient", columnList = "patient_profile_id"),
        @Index(name = "idx_reports_doctor", columnList = "doctor_profile_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class ClinicalReport {

    @Id
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @Column(name = "patient_profile_id", nullable = false, columnDefinition = "UUID")
    private UUID patientProfileId;

    @Column(name = "doctor_profile_id", nullable = false, columnDefinition = "UUID")
    private UUID doctorProfileId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "summarized_at")
    private Instant summarizedAt;

    public void markReviewed(Instant now) {
        if (status != ReportStatus.NEW) {
            throw new InvalidRequestException("Only a new report can be marked reviewed");
        }
        this.status = ReportStatus.REVIEWED;
        this.reviewedAt = now;
    }

    public void applySummary(String plainLanguageSummary, Instant now) {
        if (plainLanguageSummary == null || plainLanguageSummary.isBlank()) {
            throw new InvalidRequestException("Summary text is required");
        }
        this.summary = plainLanguageSummary.trim();
        this.summarizedAt = now;
    }

    public boolean hasSummary() {
        return summary != null && !summary.isBlank();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
