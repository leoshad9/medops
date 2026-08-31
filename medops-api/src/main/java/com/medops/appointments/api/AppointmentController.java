package com.medops.appointments.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medops.appointments.api.dto.AppointmentPageResponse;
import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.api.dto.BookAppointmentRequest;
import com.medops.appointments.api.dto.RescheduleAppointmentRequest;
import com.medops.appointments.application.AppointmentQueryService;
import com.medops.appointments.application.AppointmentTransitionService;
import com.medops.appointments.application.BookAppointmentService;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.idempotency.application.IdempotencyExecutor;
import com.medops.shared.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final BookAppointmentService bookAppointmentService;
    private final AppointmentQueryService appointmentQueryService;
    private final AppointmentTransitionService appointmentTransitionService;
    private final IdempotencyExecutor idempotencyExecutor;

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookAppointmentRequest request) {
        String requestHash = IdempotencyExecutor.sha256(
                String.valueOf(request.doctorId()),
                String.valueOf(request.startsAt()),
                request.reason());
        AppointmentResponse response = idempotencyExecutor.execute(
                authentication.getName(),
                "appointments.book",
                idempotencyKey,
                requestHash,
                AppointmentResponse.class,
                () -> bookAppointmentService.book(authentication.getName(), request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Appointment booked"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<AppointmentPageResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AppointmentPageResponse response = appointmentQueryService.list(
                authentication.getName(), status, from, to, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> get(
            Authentication authentication,
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentQueryService.get(appointmentId, authentication.getName())));
    }

    @PostMapping("/{appointmentId}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(
            Authentication authentication,
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentTransitionService.cancel(appointmentId, authentication.getName()),
                "Appointment cancelled"));
    }

    @PostMapping("/{appointmentId}/reschedule")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(
            Authentication authentication,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody RescheduleAppointmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentTransitionService.reschedule(
                        appointmentId, authentication.getName(), request),
                "Appointment rescheduled"));
    }

    @PostMapping("/{appointmentId}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> complete(
            Authentication authentication,
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.success(
                appointmentTransitionService.complete(appointmentId, authentication.getName()),
                "Appointment completed"));
    }
}
