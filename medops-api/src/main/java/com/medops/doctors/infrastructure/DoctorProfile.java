package com.medops.doctors.infrastructure;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A doctor's clinical-facing profile. References the owning {@code User} by id only
 * ({@code userId}, not a JPA relationship) so the {@code doctors} capability stays
 * decoupled from {@code auth}'s persistence model.
 */
@Entity
@Table(name = "doctor_profiles", schema = "doctors", indexes = {
        @Index(name = "idx_doctor_profiles_user_id", columnList = "user_id"),
        @Index(name = "idx_doctor_profiles_license_number", columnList = "license_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class DoctorProfile {

    @Id
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String specialty;

    @Column(name = "license_number", nullable = false, unique = true, length = 100)
    @ToString.Include
    private String licenseNumber;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
