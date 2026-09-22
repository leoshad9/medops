package com.medops.me.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.medops.auth.security.principal.MedOpsUser;
import com.medops.doctors.api.dto.DoctorProfileResponse;
import com.medops.doctors.application.DoctorProfileService;
import com.medops.me.api.dto.MeResponse;
import com.medops.patients.api.dto.PatientProfileResponse;
import com.medops.patients.application.PatientProfileService;

import lombok.RequiredArgsConstructor;

/**
 * Builds the SPA's session bootstrap payload from the authenticated principal. The UI
 * used to restore a session with a serial {@code /auth/me -> /v1/{patients|doctors}/me}
 * pair, where the second request could not start until the first returned the role;
 * this collapses both into the one round trip that already gates the first render.
 * <p>
 * Deliberately not {@code @Transactional}: each profile lookup runs inside the reading
 * service's own transaction, so a profile failure can be caught without marking a wider
 * transaction rollback-only. The profile is best-effort by design — identity must
 * survive a profile hiccup, and the portal layouts keep their dedicated profile
 * endpoint plus retry state as the fallback.
 * <p>
 * Lives in its own module because it depends on both the patients and doctors modules,
 * which already depend on auth; putting it in auth would create a package cycle.
 */
@Service
@RequiredArgsConstructor
public class MeBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(MeBootstrapService.class);

    private static final String PATIENT_ROLE = "PATIENT";
    private static final String DOCTOR_ROLE = "DOCTOR";

    private final PatientProfileService patientProfileService;
    private final DoctorProfileService doctorProfileService;

    /** Returns the caller's identity plus the profile matching the caller's role. */
    public MeResponse load(MedOpsUser principal) {
        String role = principal.getRoleName();
        return new MeResponse(
                principal.getId(),
                principal.getUsername(),
                role,
                PATIENT_ROLE.equals(role) ? patientProfile(principal.getUsername()) : null,
                DOCTOR_ROLE.equals(role) ? doctorProfile(principal.getUsername()) : null);
    }

    private PatientProfileResponse patientProfile(String email) {
        try {
            return patientProfileService.getMyProfile(email);
        } catch (RuntimeException ex) {
            log.warn("Session bootstrap could not load the patient profile for {}", email, ex);
            return null;
        }
    }

    private DoctorProfileResponse doctorProfile(String email) {
        try {
            return doctorProfileService.getMyProfile(email);
        } catch (RuntimeException ex) {
            log.warn("Session bootstrap could not load the doctor profile for {}", email, ex);
            return null;
        }
    }
}
