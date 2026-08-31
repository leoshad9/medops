package com.medops.appointments.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.medops.appointments.domain.AppointmentStatus;
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
@Table(name = "appointments", schema = "appointments", indexes = {
        @Index(name = "idx_appointments_patient", columnList = "patient_profile_id"),
        @Index(name = "idx_appointments_doctor", columnList = "doctor_profile_id"),
        @Index(name = "idx_appointments_doctor_start", columnList = "doctor_profile_id, starts_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Appointment {

    @Id
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @Column(name = "patient_profile_id", nullable = false, columnDefinition = "UUID")
    private UUID patientProfileId;

    @Column(name = "doctor_profile_id", nullable = false, columnDefinition = "UUID")
    private UUID doctorProfileId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @ToString.Include
    private AppointmentStatus status;

    @Column(length = 500)
    private String reason;

    @Column(length = 255)
    private String location;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static Appointment book(
            UUID patientProfileId,
            UUID doctorProfileId,
            Instant startsAt,
            Duration slotLength,
            String reason) {
        return Appointment.builder()
                .patientProfileId(patientProfileId)
                .doctorProfileId(doctorProfileId)
                .startsAt(startsAt)
                .endsAt(startsAt.plus(slotLength))
                .status(AppointmentStatus.BOOKED)
                .reason(reason)
                .build();
    }

    public void cancel(Instant now) {
        requireBooked("cancelled");
        if (!now.isBefore(startsAt)) {
            throw new InvalidRequestException("Appointments cannot be cancelled after they start");
        }
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void reschedule(Instant newStart, Duration slotLength, Instant now) {
        requireBooked("rescheduled");
        if (!now.isBefore(startsAt)) {
            throw new InvalidRequestException("Appointments cannot be rescheduled after they start");
        }
        this.startsAt = newStart;
        this.endsAt = newStart.plus(slotLength);
    }

    public void complete(Instant now) {
        requireBooked("completed");
        if (now.isBefore(startsAt)) {
            throw new InvalidRequestException("A visit cannot be completed before it starts");
        }
        this.status = AppointmentStatus.COMPLETED;
        this.completedAt = now;
    }

    private void requireBooked(String pastTense) {
        if (status != AppointmentStatus.BOOKED) {
            throw new InvalidRequestException("Only a booked appointment can be " + pastTense);
        }
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
