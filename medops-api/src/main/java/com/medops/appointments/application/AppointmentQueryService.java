package com.medops.appointments.application;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.medops.appointments.api.dto.AppointmentPageResponse;
import com.medops.appointments.domain.AppointmentStatus;

import lombok.RequiredArgsConstructor;

/**
 * Dispatcher for appointment listing. The actual {@code @Transactional} read
 * methods live in {@link AppointmentQueryReadService} so they are always
 * reached through the Spring proxy - never via {@code this.} from a sibling
 * method, which is what Sonar flags as S6809.
 */
@Service
@RequiredArgsConstructor
public class AppointmentQueryService {

    private final AppointmentQueryReadService readService;
    private final AppointmentActorResolver actorResolver;

    public AppointmentPageResponse list(
            String email, AppointmentStatus status, Instant from, Instant to, int page, int size) {
        if (actorResolver.findDoctor(email).isPresent()) {
            return readService.listForDoctor(email, status, from, to, page, size);
        }
        return readService.listForPatient(email, status, page, size);
    }
}
