package com.medops.doctors.api.dto;

import java.time.Instant;
import java.util.List;

import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.reports.api.dto.ClinicalReportResponse;

/**
 * Aggregated, self-service dashboard payload for the signed-in doctor. Every
 * value is derived from the same repository queries that back the detailed
 * pages (appointments list, labs list) so the dashboard totals can never
 * disagree with what the clinician sees when they open those pages.
 */
public record DoctorDashboardResponse(
        long todayScheduleCount,
        long completedTodayCount,
        long awaitingTodayCount,
        long pendingLabReportsCount,
        List<AppointmentResponse> todayAppointments,
        List<AppointmentResponse> upcomingAppointments,
        List<ClinicalReportResponse> pendingLabReports,
        Instant generatedAt
) {
}