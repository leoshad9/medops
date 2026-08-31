package com.medops.clinical;

import java.util.List;
import java.util.UUID;

/**
 * Whether a doctor may attach or read clinical files for a patient. Today this is
 * derived from appointments; other care relationships can implement the same port later.
 */
public interface CareRelationship {

    boolean doctorMayTreat(UUID doctorProfileId, UUID patientProfileId);

    List<UUID> patientIdsForDoctor(UUID doctorProfileId);
}
