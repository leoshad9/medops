package com.medops.doctors.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.appointments.application.AppointmentResponseAssembler;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.doctors.api.dto.DoctorDashboardResponse;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.patients.domain.Gender;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.application.ClinicalReportAssembler;
import com.medops.reports.domain.ReportStatus;
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.reports.infrastructure.ClinicalReportRepository;

/**
 * Unit tests for {@link DoctorDashboardService}: verifies the day window is
 * derived from the clinic time zone (not UTC), cancelled visits are excluded,
 * and the counts reconcile exactly with the returned detail lists.
 */
@ExtendWith(MockitoExtension.class)
class DoctorDashboardServiceTest {

    private static final String DOCTOR_EMAIL = "doctor@medops.dev";
    private static final UUID DOCTOR_ID = UUID.randomUUID();

    /** Fixed "now": 2026-09-20 05:00 UTC == 10:30 Asia/Kolkata, so the clinic day is 2026-09-20. */
    private static final Instant NOW = Instant.parse("2026-09-20T05:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-09-19T18:30:00Z"); // 2026-09-20T00:00+05:30
    private static final Instant DAY_END = Instant.parse("2026-09-20T18:30:00Z"); // 2026-09-21T00:00+05:30

    @Mock
    private AppointmentActorResolver actorResolver;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentResponseAssembler appointmentAssembler;

    @Mock
    private ClinicalReportRepository reportRepository;

    @Mock
    private ClinicalReportAssembler reportAssembler;

    @Mock
    private Clock clock;

    private DoctorDashboardService service;

    @BeforeEach
    void setUp() {
        service = new DoctorDashboardService(
                actorResolver,
                appointmentRepository,
                appointmentAssembler,
                reportRepository,
                reportAssembler,
                clock,
                ZoneId.of("Asia/Kolkata"));

        DoctorProfile doctor = DoctorProfile.builder().id(DOCTOR_ID).build();
        when(actorResolver.requireDoctor(DOCTOR_EMAIL)).thenReturn(doctor);
        when(clock.withZone(ZoneId.of("Asia/Kolkata"))).thenReturn(Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")));
        when(clock.instant()).thenReturn(NOW);
    }

    /** Matches the Pageable argument without coupling the test to its size. */
    @Test
    void dashboardCountsReconcileWithDetailListsAndExcludeCancelled() {
        Appointment bookedMorning = appointment(AppointmentStatus.BOOKED, DAY_START.plusSeconds(3600));
        Appointment completed = appointment(AppointmentStatus.COMPLETED, DAY_START.plusSeconds(1800));
        Appointment bookedAfternoon = appointment(AppointmentStatus.BOOKED, DAY_START.plusSeconds(7200));
        Appointment cancelled = appointment(AppointmentStatus.CANCELLED, DAY_START.plusSeconds(5400));
        when(appointmentRepository
                .findByDoctorProfileIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                        eq(DOCTOR_ID), eq(DAY_START), eq(DAY_END), anyPageable()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(bookedMorning, completed, bookedAfternoon, cancelled)));
        when(appointmentAssembler.toResponse(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment source = invocation.getArgument(0);
            return response("Patient " + source.getStatus(), source.getStatus());
        });

        DoctorDashboardResponse result = service.getMyDashboard(DOCTOR_EMAIL);

        assertThat(result.todayScheduleCount()).isEqualTo(3); // cancelled excluded
        assertThat(result.todayAppointments()).hasSize(3);
        assertThat(result.completedTodayCount()).isEqualTo(1);
        assertThat(result.awaitingTodayCount()).isEqualTo(2);
        assertThat(result.upcomingAppointments()).hasSize(2);
        assertThat(result.upcomingAppointments())
                .extracting(AppointmentResponse::status)
                .containsOnly(AppointmentStatus.BOOKED);
        assertThat(result.pendingLabReportsCount()).isZero();
        assertThat(result.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void dashboardIncludesPendingLabReports() {
        when(appointmentRepository
                .findByDoctorProfileIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                        eq(DOCTOR_ID), eq(DAY_START), eq(DAY_END), anyPageable()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        ClinicalReport report = ClinicalReport.builder()
                .id(UUID.randomUUID())
                .patientProfileId(UUID.randomUUID())
                .doctorProfileId(DOCTOR_ID)
                .title("CBC Panel")
                .status(ReportStatus.NEW)
                .storageKey("reports/cbc.pdf")
                .originalFilename("cbc.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024)
                .createdAt(NOW)
                .build();
        when(reportRepository.findByDoctorProfileIdAndStatusOrderByCreatedAtDesc(DOCTOR_ID, ReportStatus.NEW))
                .thenReturn(List.of(report));
        ClinicalReportResponse reportResponse = new ClinicalReportResponse(
                report.getId(), report.getPatientProfileId(), DOCTOR_ID, "Test Patient", "MRN-1",
                "Dr. Test", "CBC Panel", null, ReportStatus.NEW, "cbc.pdf", 1024, true,
                NOW, null, null, null);
        when(reportAssembler.toResponse(report)).thenReturn(reportResponse);

        DoctorDashboardResponse result = service.getMyDashboard(DOCTOR_EMAIL);

        assertThat(result.pendingLabReportsCount()).isEqualTo(1);
        assertThat(result.pendingLabReports()).containsExactly(reportResponse);
        verify(reportRepository).findByDoctorProfileIdAndStatusOrderByCreatedAtDesc(DOCTOR_ID, ReportStatus.NEW);
    }

    @Test
    void dashboardReturnsZerosForEmptyClinicDay() {
        when(appointmentRepository
                .findByDoctorProfileIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                        eq(DOCTOR_ID), eq(DAY_START), eq(DAY_END), anyPageable()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(reportRepository.findByDoctorProfileIdAndStatusOrderByCreatedAtDesc(DOCTOR_ID, ReportStatus.NEW))
                .thenReturn(List.of());

        DoctorDashboardResponse result = service.getMyDashboard(DOCTOR_EMAIL);

        assertThat(result.todayScheduleCount()).isZero();
        assertThat(result.completedTodayCount()).isZero();
        assertThat(result.awaitingTodayCount()).isZero();
        assertThat(result.pendingLabReportsCount()).isZero();
        assertThat(result.upcomingAppointments()).isEmpty();
        assertThat(result.pendingLabReports()).isEmpty();
    }

    /** Matches the Pageable argument without coupling the test to its size. */
    private static Pageable anyPageable() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static Appointment appointment(AppointmentStatus status, Instant startsAt) {
        return Appointment.builder()
                .id(UUID.randomUUID())
                .patientProfileId(UUID.randomUUID())
                .doctorProfileId(DOCTOR_ID)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(1800))
                .status(status)
                .build();
    }

    private static AppointmentResponse response(String patientName, AppointmentStatus status) {
        return new AppointmentResponse(
                UUID.randomUUID(), UUID.randomUUID(), DOCTOR_ID, patientName, "MRN-2026-000001",
                LocalDate.of(1990, 1, 1), Gender.FEMALE, "Dr. Test", "General Medicine",
                DAY_START, DAY_START.plusSeconds(1800), status, "Checkup", null);
    }
}