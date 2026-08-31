package com.medops.prescriptions.infrastructure;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.medops.prescriptions.domain.PrescriptionStatus;

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
@Table(name = "prescriptions", schema = "prescriptions", indexes = {
        @Index(name = "idx_prescriptions_patient", columnList = "patient_profile_id"),
        @Index(name = "idx_prescriptions_doctor", columnList = "doctor_profile_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Prescription {

    @Id
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @Column(name = "patient_profile_id", nullable = false, columnDefinition = "UUID")
    private UUID patientProfileId;

    @Column(name = "doctor_profile_id", nullable = false, columnDefinition = "UUID")
    private UUID doctorProfileId;

    @Column(name = "medication_name", nullable = false, length = 200)
    private String medicationName;

    @Column(nullable = false, length = 200)
    private String dosage;

    @Column(nullable = false, length = 1000)
    private String instructions;

    @Column(name = "refills_remaining", nullable = false)
    private int refillsRemaining;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PrescriptionStatus status;

    @Column(name = "storage_key", length = 255)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
