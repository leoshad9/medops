package com.medops.appointments.api.dto;

import java.time.Instant;
import java.util.List;

public record SlotListResponse(List<Instant> slots) {
}
