package com.medops.doctors.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {

    Optional<DoctorProfile> findByUserId(UUID userId);

    /**
     * Row-locks the doctor for the rest of the current transaction. Booking and
     * rescheduling take this before checking slot availability so two transactions
     * cannot both observe a free slot and both commit.
     *
     * <p>Locks the doctor rather than the slot because a slot with no appointment has
     * no row to lock. The cost is that concurrent claims on <em>different</em> slots of
     * the same doctor serialise too, which a per-doctor schedule makes acceptable.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DoctorProfile d where d.id = :doctorId")
    Optional<DoctorProfile> findByIdForUpdate(@Param("doctorId") UUID doctorId);

    boolean existsByLicenseNumber(String licenseNumber);

    List<DoctorProfile> findAllByOrderByFullNameAsc();

    List<DoctorProfile> findBySpecialtyIgnoreCaseOrderByFullNameAsc(String specialty);
}
