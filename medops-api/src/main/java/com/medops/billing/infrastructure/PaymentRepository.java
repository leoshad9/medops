package com.medops.billing.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medops.billing.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    @Query("SELECT COALESCE(SUM(p.amountCents), 0) FROM Payment p"
            + " WHERE p.invoice.id = :invoiceId AND p.status = :status")
    long sumAmountCentsByInvoiceIdAndStatus(
            @Param("invoiceId") UUID invoiceId, @Param("status") PaymentStatus status);
}
