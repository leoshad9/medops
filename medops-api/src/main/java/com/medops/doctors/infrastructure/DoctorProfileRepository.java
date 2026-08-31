package com.medops.doctors.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {

    Optional<DoctorProfile> findByUserId(UUID userId);

    boolean existsByLicenseNumber(String licenseNumber);

    List<DoctorProfile> findAllByOrderByFullNameAsc();

    List<DoctorProfile> findBySpecialtyIgnoreCaseOrderByFullNameAsc(String specialty);
}
