package com.medops.billing.infrastructure;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByPatientProfileIdOrderByCreatedAtDesc(UUID patientProfileId, Pageable pageable);
}
