package com.medops.billing.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.medops.billing.domain.InvoiceStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
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
@Table(name = "invoices", schema = "billing", indexes = {
        @Index(name = "idx_invoices_patient", columnList = "patient_profile_id"),
        @Index(name = "idx_invoices_appointment", columnList = "appointment_id"),
        @Index(name = "idx_invoices_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Invoice {

    @Id
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @Column(name = "patient_profile_id", nullable = false, columnDefinition = "UUID")
    private UUID patientProfileId;

    @Column(name = "appointment_id", columnDefinition = "UUID")
    private UUID appointmentId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @ToString.Include
    private InvoiceStatus status;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public long totalCents() {
        return items.stream().mapToLong(i -> i.getUnitPriceCents() * i.getQuantity()).sum();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
