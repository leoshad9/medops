package com.medops.shared.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link AuditEvent}. Insert-only from the application's perspective - there is
 * intentionally no update/delete use case here.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findBySubjectId(UUID subjectId);
}
