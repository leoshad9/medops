package com.medops.appointments.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medops.appointments.domain.AppointmentStatus;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByDoctorProfileIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            UUID doctorProfileId, AppointmentStatus status, Instant fromInclusive, Instant toExclusive);

    boolean existsByDoctorProfileIdAndStatusAndStartsAt(
            UUID doctorProfileId, AppointmentStatus status, Instant startsAt);

    java.util.Optional<Appointment> findByDoctorProfileIdAndStatusAndStartsAt(
            UUID doctorProfileId, AppointmentStatus status, Instant startsAt);

    long countByDoctorProfileIdAndStatusAndStartsAt(
            UUID doctorProfileId, AppointmentStatus status, Instant startsAt);

    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.patientProfileId = :patientId
              AND a.status = :status
              AND a.startsAt < :endsAt
              AND a.endsAt > :startsAt
              AND (:excludeId IS NULL OR a.id <> :excludeId)
            """)
    boolean existsOverlappingForPatient(
            @Param("patientId") UUID patientId,
            @Param("status") AppointmentStatus status,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("excludeId") UUID excludeId);

    Page<Appointment> findByPatientProfileIdOrderByStartsAtDesc(UUID patientProfileId, Pageable pageable);

    Page<Appointment> findByPatientProfileIdAndStatusOrderByStartsAtDesc(
            UUID patientProfileId, AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByDoctorProfileIdOrderByStartsAtAsc(UUID doctorProfileId, Pageable pageable);

    Page<Appointment> findByDoctorProfileIdAndStatusOrderByStartsAtAsc(
            UUID doctorProfileId, AppointmentStatus status, Pageable pageable);

    Page<Appointment> findByDoctorProfileIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
            UUID doctorProfileId, Instant fromInclusive, Instant toExclusive, Pageable pageable);

    boolean existsByDoctorProfileIdAndPatientProfileIdAndStatusIn(
            UUID doctorProfileId, UUID patientProfileId, Collection<AppointmentStatus> statuses);

    @Query("""
            SELECT DISTINCT a.patientProfileId FROM Appointment a
            WHERE a.doctorProfileId = :doctorId
              AND a.status IN :statuses
            """)
    List<UUID> findDistinctPatientProfileIdsByDoctorProfileIdAndStatusIn(
            @Param("doctorId") UUID doctorId,
            @Param("statuses") Collection<AppointmentStatus> statuses);
}
