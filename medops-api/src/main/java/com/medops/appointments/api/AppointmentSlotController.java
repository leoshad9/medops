package com.medops.appointments.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medops.appointments.api.dto.SlotListResponse;
import com.medops.appointments.application.BookAppointmentService;
import com.medops.shared.response.ApiResponse;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1/doctors/{doctorId}/slots")
@RequiredArgsConstructor
public class AppointmentSlotController {

    private final BookAppointmentService bookAppointmentService;

    @GetMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<SlotListResponse>> listSlots(
            @PathVariable UUID doctorId,
            @RequestParam @NotNull LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                new SlotListResponse(bookAppointmentService.listAvailableSlots(doctorId, date))));
    }
}
