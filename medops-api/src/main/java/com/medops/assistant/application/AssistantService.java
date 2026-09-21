package com.medops.assistant.application;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.medops.appointments.api.dto.AppointmentResponse;
import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.appointments.application.AppointmentQueryService;
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
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.prescriptions.api.dto.PrescriptionResponse;
import com.medops.prescriptions.application.PrescriptionQueryService;
import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.application.ReportQueryService;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates a single assistant chat turn: rate limiting, actor resolution,
 * read-only context enrichment (appointments, lab reports, prescriptions, invoices,
 * and medical records), AI client invocation, and auditing. Never logs the user
 * message, the reply, or the context snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final String RATE_LIMIT_KEY_PREFIX = "assistant:chat:";
    private static final int MAX_CHATS_PER_WINDOW = 20;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(5);

    /** Upper bound on each snapshot list, mirroring the sidecar's per-list limits. */
    private static final int MAX_CONTEXT_ITEMS = 10;
    /** Mirrors the sidecar's per-field limit so the payload can never be rejected. */
    private static final int MAX_CONTEXT_FIELD_CHARS = 200;
    private static final ZoneId UTC_FALLBACK = ZoneId.of("UTC");
    private static final DateTimeFormatter CONTEXT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm", Locale.ENGLISH);
    /** Mirrors the portal's Lab Reports versus Medical Records split of clinical reports. */
    private static final Pattern LAB_TITLE_PATTERN =
            Pattern.compile("\\blab\\b|\\bblood\\b|\\bpatholog", Pattern.CASE_INSENSITIVE);
    /** The portal stores one clinical-report entity, so chart uploads surface as documents. */
    private static final String MEDICAL_RECORD_TYPE = "CLINICAL_DOCUMENT";

    private final AssistantClient assistantClient;
    private final RateLimiterStore rateLimiterStore;
    private final AppointmentActorResolver actorResolver;
    private final AppointmentQueryService appointmentQueryService;
    private final ReportQueryService reportQueryService;
    private final PrescriptionQueryService prescriptionQueryService;
    private final InvoiceService invoiceService;
    private final AuditService auditService;

    /**
     * Processes one chat turn for the authenticated user.
     *
     * <p>Identity comes exclusively from the authenticated principal (email);
     * no user/patient identifiers are ever accepted from the client. The reply is
     * grounded in a bounded, LLM-safe snapshot of the caller's own records; when a
     * section cannot be read the chat still proceeds without it rather than failing.
     *
     * @param email the authenticated user's email
     * @param message the validated, non-blank user message
     * @return the assistant reply wrapped in the API response payload
     */
    public AssistantChatResponse chat(String email, String message) {
        return chat(email, message, null);
    }

    /**
     * Processes one chat turn for the authenticated user.
     *
     * <p>Identity comes exclusively from the authenticated principal (email);
     * no user/patient identifiers are ever accepted from the client. The reply is
     * grounded in a bounded, LLM-safe snapshot of the caller's own appointments,
     * lab reports, prescriptions, invoices, and medical records; any section that
     * cannot be read is simply left out, so degraded enrichment never turns a chat
     * into a server error.
     *
     * @param email the authenticated user's email
     * @param message the validated, non-blank user message
     * @param timeZone optional IANA zone from the client, used only to render this
     *                 user's own record times; never trusted for identity
     * @return the assistant reply wrapped in the API response payload
     */
    public AssistantChatResponse chat(String email, String message, String timeZone) {
        User user = actorResolver.requireActiveUser(email);

        // Fail-closed: RateLimiterStore implementations throw
        // ServiceUnavailableException when the backing store is unreachable.
        if (!rateLimiterStore.tryAcquire(RATE_LIMIT_KEY_PREFIX + user.getId(),
                MAX_CHATS_PER_WINDOW, RATE_LIMIT_WINDOW)) {
            throw new AssistantRateLimitException();
        }

        ZoneId zone = resolveZone(timeZone);
        AssistantContext context = buildContext(email, zone);

        AssistantReply reply = assistantClient.chat(message, context, zone.getId());

        // Success event only; the message and reply text are never persisted or logged.
        auditService.recordEvent(AuditEventType.ASSISTANT_CHAT, user.getId(), email);
        return new AssistantChatResponse(reply.text());
    }

    /**
     * Assembles the bounded, LLM-safe snapshot attached to the caller's message.
     * Every section is read through the standard authorized query paths, so a caller
     * only ever contributes their own data.
     *
     * @param email the authenticated user's email
     * @param zone the zone the snapshot times must be rendered in
     * @return the aggregate context, with any unavailable section left empty
     */
    private AssistantContext buildContext(String email, ZoneId zone) {
        List<ClinicalReportResponse> reports = recentReports(email);
        return AssistantContext.of(
                upcomingAppointments(email, zone),
                reports.stream()
                        .filter(report -> isLabTitle(report.title()))
                        .map(report -> toLabReport(report, zone))
                        .toList(),
                prescriptions(email, zone),
                invoices(email, zone),
                reports.stream()
                        .filter(report -> !isLabTitle(report.title()))
                        .map(report -> toMedicalRecord(report, zone))
                        .toList());
    }

    /**
     * Reads the caller's own upcoming appointments through the standard authorized
     * query path, so a patient sees only their appointments and a doctor only their
     * own schedule.
     *
     * <p>Best-effort by design: any failure yields an empty snapshot plus a warning,
     * so degraded enrichment can never turn a chat into a server error. Only
     * LLM-safe fields are mapped, and times are pre-rendered in the caller's zone so
     * the model never performs timezone arithmetic.
     *
     * @param email the authenticated user's email
     * @param zone the zone the snapshot times must be rendered in
     * @return a soonest-first snapshot of at most {@value #MAX_CONTEXT_ITEMS}
     *         upcoming appointments, or an empty list when unavailable
     */
    private List<AssistantAppointment> upcomingAppointments(String email, ZoneId zone) {
        try {
            Instant now = Instant.now();
            return appointmentQueryService.list(email, null, null, null, 0, MAX_CONTEXT_ITEMS)
                    .items().stream()
                    .filter(appointment -> appointment.startsAt().isAfter(now))
                    .sorted(Comparator.comparing(AppointmentResponse::startsAt))
                    .limit(MAX_CONTEXT_ITEMS)
                    .map(appointment -> toContext(appointment, zone))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Assistant appointment context unavailable; continuing without it", ex);
            return List.of();
        }
    }

    /** Maps one appointment to the identifier-free projection sent to the AI service. */
    private AssistantAppointment toContext(AppointmentResponse appointment, ZoneId zone) {
        return new AssistantAppointment(
                CONTEXT_TIME_FORMAT.withZone(zone).format(appointment.startsAt()),
                appointment.status().name(),
                clip(appointment.doctorName()),
                clip(appointment.specialty()),
                clip(appointment.location()));
    }

    /**
     * Reads the caller's most recent clinical reports, newest first.
     *
     * @param email the authenticated user's email
     * @return at most {@value #MAX_CONTEXT_ITEMS} reports, or an empty list when unavailable
     */
    private List<ClinicalReportResponse> recentReports(String email) {
        try {
            return reportQueryService.list(email, null).stream()
                    .limit(MAX_CONTEXT_ITEMS)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Assistant clinical report context unavailable; continuing without it", ex);
            return List.of();
        }
    }

    /**
     * Reads the caller's own prescriptions: a patient sees the ones written for them,
     * a doctor the ones they authored.
     *
     * @param email the authenticated user's email
     * @param zone the zone the snapshot times must be rendered in
     * @return at most {@value #MAX_CONTEXT_ITEMS} prescriptions, or an empty list when unavailable
     */
    private List<AssistantPrescription> prescriptions(String email, ZoneId zone) {
        try {
            return prescriptionQueryService.list(email, null).stream()
                    .limit(MAX_CONTEXT_ITEMS)
                    .map(prescription -> toPrescription(prescription, zone))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Assistant prescription context unavailable; continuing without it", ex);
            return List.of();
        }
    }

    /**
     * Reads the caller's own invoices, newest first. Billing is a patient-facing
     * concern, so a doctor contributes no billing context.
     *
     * @param email the authenticated user's email
     * @param zone the zone the snapshot times must be rendered in
     * @return at most {@value #MAX_CONTEXT_ITEMS} invoices, or an empty list when unavailable
     */
    private List<AssistantInvoice> invoices(String email, ZoneId zone) {
        if (actorResolver.findDoctor(email).isPresent()) {
            return List.of();
        }
        try {
            PatientProfile patient = actorResolver.requirePatient(email);
            return invoiceService.listForPatient(patient.getId(), PageRequest.of(0, MAX_CONTEXT_ITEMS))
                    .getContent().stream()
                    .map(invoice -> toInvoice(invoice, zone))
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Assistant billing context unavailable; continuing without it", ex);
            return List.of();
        }
    }

    /** Maps one clinical report to the identifier-free lab-report projection. */
    private static AssistantLabReport toLabReport(ClinicalReportResponse report, ZoneId zone) {
        return new AssistantLabReport(
                formatTime(report.createdAt(), zone),
                clip(report.title()),
                report.status() == null ? null : report.status().name(),
                clip(report.doctorName()),
                null,
                hasSummary(report.summary()),
                clip(report.summary()));
    }

    /**
     * Maps one clinical report to the identifier-free medical-record projection. The
     * portal keeps lab results and chart documents in the same table, so anything
     * that is not a lab report is surfaced as an uploaded clinical document.
     */
    private static AssistantMedicalRecord toMedicalRecord(ClinicalReportResponse report, ZoneId zone) {
        return new AssistantMedicalRecord(
                formatTime(report.createdAt(), zone),
                clip(report.title()),
                MEDICAL_RECORD_TYPE,
                clip(report.doctorName()),
                null,
                hasSummary(report.summary()),
                clip(report.summary()));
    }

    /** Maps one prescription to the identifier-free projection sent to the AI service. */
    private static AssistantPrescription toPrescription(PrescriptionResponse prescription, ZoneId zone) {
        return new AssistantPrescription(
                formatTime(prescription.createdAt(), zone),
                clip(prescription.medicationName()),
                clip(prescription.dosage()),
                prescription.status() == null ? null : prescription.status().name(),
                clip(prescription.doctorName()),
                null,
                prescription.refillsRemaining());
    }

    /**
     * Maps one invoice to the identifier-free projection sent to the AI service. The
     * portal does not model an appointment type, so that slot stays empty; the due
     * date is a calendar date, so it is sent as an ISO date rather than a local time.
     */
    private static AssistantInvoice toInvoice(InvoiceResponse invoice, ZoneId zone) {
        return new AssistantInvoice(
                formatTime(invoice.createdAt(), zone),
                invoice.status() == null ? null : invoice.status().name(),
                invoice.totalCents(),
                invoice.paidCents(),
                invoice.balanceCents(),
                invoice.dueDate() == null ? null : invoice.dueDate().toString(),
                null);
    }

    /** Mirrors the portal's heuristic for showing a clinical report as a lab result. */
    private static boolean isLabTitle(String title) {
        return title != null && LAB_TITLE_PATTERN.matcher(title).find();
    }

    /** Whether an AI summary is present, so the model knows to ask for it otherwise. */
    private static boolean hasSummary(String summary) {
        return summary != null && !summary.isBlank();
    }

    /** Renders a moment in the caller's zone so the model never performs timezone math. */
    private static String formatTime(TemporalAccessor moment, ZoneId zone) {
        return CONTEXT_TIME_FORMAT.withZone(zone).format(moment);
    }

    /** Resolves the client-supplied zone, falling back to UTC when absent or unknown. */
    private static ZoneId resolveZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return UTC_FALLBACK;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException ex) {
            // Client input only: a bad zone must never fail the chat nor reach the model.
            return UTC_FALLBACK;
        }
    }

    /** Collapses whitespace and truncates to the sidecar's per-field limit. */
    private static String clip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= MAX_CONTEXT_FIELD_CHARS
                ? collapsed
                : collapsed.substring(0, MAX_CONTEXT_FIELD_CHARS);
    }
}
