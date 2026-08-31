package com.medops.appointments.api.dto;

import java.util.List;

public record AppointmentPageResponse(
        List<AppointmentResponse> items,
        int page,
        int size,
        long total
) {
}
