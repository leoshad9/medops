package com.medops.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.medops.appointments.api.dto.AppointmentPageResponse;
import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.appointments.application.AppointmentQueryService;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.assistant.api.dto.AssistantChatResponse;
import com.medops.assistant.domain.AssistantAppointment;
import com.medops.assistant.domain.AssistantClient;
import com.medops.assistant.domain.AssistantContext;
import com.medops.assistant.domain.AssistantInvoice;
import com.medops.assistant.domain.AssistantLabReport;
import com.medops.assistant.domain.AssistantMedicalRecord;
import com.medops.assistant.domain.AssistantPrescription;
import com.medops.assistant.domain.AssistantRateLimitException;
import com.medops.assistant.domain.AssistantReply;
import com.medops.auth.domain.User;
import com.medops.billing.api.dto.InvoiceResponse;
import com.medops.billing.application.InvoiceService;
import com.medops.billing.domain.InvoiceStatus;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.patients.domain.Gender;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.prescriptions.api.dto.PrescriptionResponse;
import com.medops.prescriptions.application.PrescriptionQueryService;
import com.medops.prescriptions.domain.PrescriptionStatus;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.application.ReportQueryService;
import com.medops.reports.domain.ReportStatus;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ServiceUnavailableException;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    private static final String EMAIL = "patient@medops.dev";
    private static final String ZONE = "Asia/Kolkata";
    private static final int CONTEXT_PAGE_SIZE = 10;

    @Mock
    private AssistantClient assistantClient;
    @Mock
    private RateLimiterStore rateLimiterStore;
    @Mock
    private AppointmentActorResolver actorResolver;
    @Mock
    private AppointmentQueryService appointmentQueryService;
    @Mock
    private ReportQueryService reportQueryService;
    @Mock
    private PrescriptionQueryService prescriptionQueryService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private AuditService auditService;
    @Mock
    private DoctorProfile doctorProfile;
    @Mock
    private PatientProfile patientProfile;

    private AssistantService service;
    private User user;

    /** Creates the service under test and an authenticated user fixture. */
    @BeforeEach
    void setUp() {
        service = new AssistantService(
                assistantClient, rateLimiterStore, actorResolver, appointmentQueryService,
                reportQueryService, prescriptionQueryService, invoiceService, auditService);
        user = User.builder().id(UUID.randomUUID()).email(EMAIL).build();
        // A doctor has no patient billing context; the billing test overrides this.
        lenient().when(actorResolver.findDoctor(EMAIL)).thenReturn(Optional.of(doctorProfile));
    }

    /** Verifies that chat grounds the reply in the caller's own upcoming appointments. */
    @Test
    void chatSendsMappedUpcomingAppointmentsInCallerZone() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(appointmentQueryService.list(EMAIL, null, null, null, 0, CONTEXT_PAGE_SIZE))
                .thenReturn(pageOf(
                        appointment("2026-09-25T05:00:00Z", "Dr. Rao", "Cardiology", "Room 3"),
                        appointment("2026-09-23T05:00:00Z", "Dr. Rao", "Cardiology", "Room 3"),
                        appointment("2020-01-01T05:00:00Z", "Dr. Past", null, null)));
        // Past entries are dropped and the snapshot is re-ordered soonest-first.
        AssistantContext expected = appointmentsOnly(List.of(
                new AssistantAppointment("Wed, 23 Sep 2026 10:30", "BOOKED", "Dr. Rao", "Cardiology", "Room 3"),
                new AssistantAppointment("Fri, 25 Sep 2026 10:30", "BOOKED", "Dr. Rao", "Cardiology", "Room 3")));
        when(assistantClient.chat("Hello", expected, ZONE)).thenReturn(new AssistantReply("Hi there!"));

        AssistantChatResponse response = service.chat(EMAIL, "Hello", ZONE);

        assertThat(response.message()).isEqualTo("Hi there!");
        verify(assistantClient).chat("Hello", expected, ZONE);
    }

    /** Verifies that nothing upcoming means no appointment context at all. */
    @Test
    void chatSendsNoContextWhenNothingIsUpcoming() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(appointmentQueryService.list(EMAIL, null, null, null, 0, CONTEXT_PAGE_SIZE))
                .thenReturn(pageOf(appointment("2020-01-01T05:00:00Z", "Dr. Past", null, null)));
        when(assistantClient.chat("Hello", AssistantContext.empty(), ZONE)).thenReturn(new AssistantReply("Hi"));

        service.chat(EMAIL, "Hello", ZONE);

        verify(assistantClient).chat("Hello", AssistantContext.empty(), ZONE);
    }

    /**
     * Verifies that enrichment failures degrade to an empty snapshot instead of
     * turning the chat into a server error.
     */
    @Test
    void chatDegradesToNoContextWhenAppointmentLookupFails() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(appointmentQueryService.list(EMAIL, null, null, null, 0, CONTEXT_PAGE_SIZE))
                .thenThrow(new ServiceUnavailableException("Appointments unavailable"));
        when(assistantClient.chat("Hello", AssistantContext.empty(), "UTC")).thenReturn(new AssistantReply("Hi"));

        AssistantChatResponse response = service.chat(EMAIL, "Hello", null);

        assertThat(response.message()).isEqualTo("Hi");
        verify(auditService).recordEvent(AuditEventType.ASSISTANT_CHAT, user.getId(), EMAIL);
    }

    /** Verifies that an unusable client time zone falls back to UTC for the snapshot. */
    @Test
    void chatFallsBackToUtcWhenTimeZoneIsInvalid() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(appointmentQueryService.list(EMAIL, null, null, null, 0, CONTEXT_PAGE_SIZE))
                .thenReturn(pageOf(appointment("2026-09-23T05:00:00Z", "Dr. Rao", "Cardiology", "Room 3")));
        AssistantContext expected = appointmentsOnly(List.of(
                new AssistantAppointment("Wed, 23 Sep 2026 05:00", "BOOKED", "Dr. Rao", "Cardiology", "Room 3")));
        when(assistantClient.chat("Hello", expected, "UTC")).thenReturn(new AssistantReply("Hi"));

        service.chat(EMAIL, "Hello", "Not/AZone");

        verify(assistantClient).chat("Hello", expected, "UTC");
    }

    /** Verifies that a successful chat records the resolved user identifier. */
    @Test
    void chatRecordsAuditEventWithResolvedUserId() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(appointmentQueryService.list(EMAIL, null, null, null, 0, CONTEXT_PAGE_SIZE))
                .thenReturn(pageOf(appointment("2026-09-23T05:00:00Z", "Dr. Rao", null, null)));
        when(assistantClient.chat(anyString(), any(AssistantContext.class), anyString()))
                .thenReturn(new AssistantReply("Hi"));

        service.chat(EMAIL, "Hello", ZONE);

        verify(auditService).recordEvent(AuditEventType.ASSISTANT_CHAT, user.getId(), EMAIL);
    }

    /** Verifies that the rate-limit key uses the server-resolved user identifier. */
    @Test
    void chatRateLimitKeyIsDerivedFromServerSideUserId() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(appointmentQueryService.list(EMAIL, null, null, null, 0, CONTEXT_PAGE_SIZE))
                .thenReturn(pageOf(appointment("2026-09-23T05:00:00Z", "Dr. Rao", null, null)));
        when(assistantClient.chat(anyString(), any(AssistantContext.class), anyString()))
                .thenReturn(new AssistantReply("Hi"));

        service.chat(EMAIL, "Hello", ZONE);

        // The key must be built from the server-resolved user id, never client input.
        verify(rateLimiterStore).tryAcquire("assistant:chat:" + user.getId(), 20, Duration.ofMinutes(5));
    }

    /** Verifies that an exhausted rate limit skips enrichment, the client, and audit. */
    @Test
    void chatThrowsAndSkipsClientAndAuditWhenRateLimited() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.chat(EMAIL, "Hello", ZONE))
                .isInstanceOf(AssistantRateLimitException.class);

        verify(appointmentQueryService, never()).list(anyString(), any(), any(), any(), anyInt(), anyInt());
        verify(assistantClient, never()).chat(anyString(), any(AssistantContext.class), any());
        verify(auditService, never()).recordEvent(any(), any(), any());
    }

    /** Verifies that an unavailable rate-limit store fails closed. */
    @Test
    void chatFailsClosedWhenRateLimiterStoreUnavailable() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new ServiceUnavailableException("Rate limit store unavailable"));

        assertThatThrownBy(() -> service.chat(EMAIL, "Hello", ZONE))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(appointmentQueryService, never()).list(anyString(), any(), any(), any(), anyInt(), anyInt());
        verify(assistantClient, never()).chat(anyString(), any(AssistantContext.class), any());
    }

    /** Verifies that a failed assistant call is not recorded as successful. */
    @Test
    void chatDoesNotAuditWhenClientFails() {
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(appointmentQueryService.list(EMAIL, null, null, null, 0, CONTEXT_PAGE_SIZE))
                .thenReturn(pageOf());
        when(assistantClient.chat(eq("Hello"), any(AssistantContext.class), eq(ZONE)))
                .thenThrow(new ServiceUnavailableException("AI assistant unavailable"));

        assertThatThrownBy(() -> service.chat(EMAIL, "Hello", ZONE))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(auditService, never()).recordEvent(any(), any(), any());
    }

    /**
     * Verifies that the snapshot carries the caller's own lab reports, prescriptions,
     * billing, and uploaded records, with lab results split from medical records.
     */
    @Test
    void chatSendsMappedClinicalAndBillingContext() {
        UUID patientProfileId = UUID.randomUUID();
        when(actorResolver.requireActiveUser(EMAIL)).thenReturn(user);
        when(rateLimiterStore.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(appointmentQueryService.list(EMAIL, null, null, null, 0, CONTEXT_PAGE_SIZE))
                .thenReturn(pageOf());
        when(reportQueryService.list(EMAIL, null)).thenReturn(List.of(
                report("2026-09-23T05:00:00Z", "Lab Panel: Lipid Profile", "Cholesterol is slightly high."),
                report("2026-09-21T02:45:00Z", "Discharge Summary", null)));
        when(prescriptionQueryService.list(EMAIL, null)).thenReturn(List.of(
                prescription("2026-09-22T03:30:00Z", "Atorvastatin", "10 mg nightly", 2)));
        when(actorResolver.findDoctor(EMAIL)).thenReturn(Optional.empty());
        when(actorResolver.requirePatient(EMAIL)).thenReturn(patientProfile);
        when(patientProfile.getId()).thenReturn(patientProfileId);
        when(invoiceService.listForPatient(patientProfileId, PageRequest.of(0, CONTEXT_PAGE_SIZE)))
                .thenReturn(new PageImpl<>(List.of(invoice("2026-09-21T02:30:00Z"))));
        AssistantContext expected = AssistantContext.of(
                List.of(),
                List.of(new AssistantLabReport(
                        "Wed, 23 Sep 2026 10:30", "Lab Panel: Lipid Profile", "NEW", "Dr. Rao", null, true,
                        "Cholesterol is slightly high.")),
                List.of(new AssistantPrescription(
                        "Tue, 22 Sep 2026 09:00", "Atorvastatin", "10 mg nightly", "ACTIVE", "Dr. Rao", null, 2)),
                List.of(new AssistantInvoice(
                        "Mon, 21 Sep 2026 08:00", "ISSUED", 12_000L, 2_000L, 10_000L, "2026-10-05", null)),
                List.of(new AssistantMedicalRecord(
                        "Mon, 21 Sep 2026 08:15", "Discharge Summary", "CLINICAL_DOCUMENT", "Dr. Rao", null, false,
                        null)));
        when(assistantClient.chat("Hello", expected, ZONE)).thenReturn(new AssistantReply("Hi"));

        service.chat(EMAIL, "Hello", ZONE);

        verify(assistantClient).chat("Hello", expected, ZONE);
    }

    /** Builds an appointment fixture that ends one hour after it starts. */
    private static AppointmentResponse appointment(
            String startsAt, String doctorName, String specialty, String location) {
        Instant start = Instant.parse(startsAt);
        return new AppointmentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Patient Example",
                "MRN-0001",
                LocalDate.of(1990, 1, 1),
                Gender.FEMALE,
                doctorName,
                specialty,
                start,
                start.plus(Duration.ofHours(1)),
                AppointmentStatus.BOOKED,
                "Routine check",
                location);
    }

    /** Wraps appointment fixtures in the paged response the query service returns. */
    private static AppointmentPageResponse pageOf(AppointmentResponse... items) {
        return new AppointmentPageResponse(List.of(items), 0, CONTEXT_PAGE_SIZE, items.length);
    }

    /** Builds a context carrying appointments only, with every other section empty. */
    private static AssistantContext appointmentsOnly(List<AssistantAppointment> appointments) {
        return AssistantContext.of(appointments, List.of(), List.of(), List.of(), List.of());
    }

    /** Builds a clinical report fixture with an optional AI summary. */
    private static ClinicalReportResponse report(String createdAt, String title, String summary) {
        Instant created = Instant.parse(createdAt);
        return new ClinicalReportResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Patient Example",
                "MRN-0001",
                "Dr. Rao",
                title,
                "Review with your doctor",
                ReportStatus.NEW,
                "report.pdf",
                1024L,
                true,
                created,
                null,
                summary,
                summary == null ? null : created);
    }

    /** Builds a prescription fixture written by a fixed doctor. */
    private static PrescriptionResponse prescription(
            String createdAt, String medicationName, String dosage, int refillsRemaining) {
        return new PrescriptionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Patient Example",
                "MRN-0001",
                "Dr. Rao",
                medicationName,
                dosage,
                "Take with water",
                refillsRemaining,
                PrescriptionStatus.ACTIVE,
                "prescription.pdf",
                true,
                Instant.parse(createdAt));
    }

    /** Builds an issued invoice fixture that is partly paid. */
    private static InvoiceResponse invoice(String createdAt) {
        return new InvoiceResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                InvoiceStatus.ISSUED,
                12_000L,
                2_000L,
                10_000L,
                LocalDate.of(2026, 10, 5),
                null,
                List.of(),
                ZonedDateTime.parse(createdAt));
    }
}
