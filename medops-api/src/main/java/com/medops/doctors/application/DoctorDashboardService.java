package com.medops.doctors.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.application.AppointmentResponseAssembler;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.doctors.api.dto.DoctorDashboardResponse;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.application.ReportQueryService;
import com.medops.reports.domain.ReportStatus;

import lombok.RequiredArgsConstructor;

/**
 * Builds the doctor dashboard aggregate. Counts, the upcoming list, and the
 * pending-labs list all come from the same repository queries that back the
 * appointments and labs detail pages, so the dashboard can never report
 * numbers that disagree with what the clinician sees on those pages.
 */
@Service
@RequiredArgsConstructor
public class DoctorDashboardService {

    /** Safety cap for today's appointment fetch; far above any real clinic day. */
    private static final int MAX_DAY_APPOINTMENTS = 200;

    private final AppointmentActorResolver actorResolver;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentResponseAssembler appointmentAssembler;
    private final ReportQueryService reportQueryService;
    private final java.time.Clock clock;
    private final ZoneId clinicTimeZone;

    @Transactional(readOnly = true)
    public DoctorDashboardResponse getMyDashboard(String doctorEmail) {
        DoctorProfile doctor = actorResolver.requireDoctor(doctorEmail);

        Instant dayStart = LocalDate.now(clock.withZone(clinicTimeZone))
                .atStartOfDay(clinicTimeZone)
                .toInstant();
        Instant dayEnd = dayStart.plusSeconds(java.time.Duration.ofDays(1).getSeconds());

        List<Appointment> todays = appointmentRepository
                .findByDoctorProfileIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                        doctor.getId(), dayStart, dayEnd, Pageable.ofSize(MAX_DAY_APPOINTMENTS))
                .filter(appointment -> appointment.getStatus() != AppointmentStatus.CANCELLED)
                .toList();

        List<AppointmentResponse> todaysResponses = todays.stream()
                .map(appointmentAssembler::toResponse)
                .toList();

        List<AppointmentResponse> upcoming = todays.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.BOOKED)
                .map(appointmentAssembler::toResponse)
                .toList();

        List<ClinicalReportResponse> pendingLabs = reportQueryService
                .listForDoctor(doctorEmail, null).stream()
                .filter(report -> report.status() == ReportStatus.NEW)
                .toList();

        return new DoctorDashboardResponse(
                (long) todays.size(),
                todays.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count(),
                (long) upcoming.size(),
                (long) pendingLabs.size(),
                todaysResponses,
                upcoming,
                pendingLabs,
                clock.instant());
    }
}
