import type { InvoiceDto } from "../services/billingService";
import type { AppointmentDto } from "../services/appointmentService";
import type { ClinicalReportDto, PrescriptionDto } from "../services/clinicalService";
import type { PatientProfile } from "../types/patient";
import {
  formatPatientDateTime,
  formatPatientDateTimeAbsolute,
  formatPatientTime,
  formatTimeRemaining,
  patientTimeZone,
  relativePart,
  sanitizePatientField,
  upcomingCardParts,
} from "./clinicTime";
import type { HealthMetric } from "../types/patient";
import type {
  AppointmentStatus,
  DashboardStat,
  NotificationItem,
  RecentAppointmentRow,
  UpcomingAppointment,
} from "../types/patient";
import { formatCents } from "../services/billingService";

export interface PatientDashboardLiveData {
  stats: DashboardStat[];
  upcomingAppointment: UpcomingAppointment | null;
  recentAppointments: RecentAppointmentRow[];
  activity: NotificationItem[];
  healthMetrics: HealthMetric[];
}

const CANCELLATION_POLICY =
  "You can reschedule or cancel free of charge up to 24 hours before your appointment.";

/** Maps a persisted API status to the patient-facing status label. */
function toPatientStatus(dtoStatus: AppointmentDto["status"]): AppointmentStatus {
  if (dtoStatus === "CANCELLED") {
    return "CANCELLED";
  }
  if (dtoStatus === "COMPLETED") {
    return "COMPLETED";
  }
  return "UPCOMING";
}

function toUpcomingCard(dto: AppointmentDto): UpcomingAppointment {
  const tz = patientTimeZone();
  const parts = upcomingCardParts(dto.startsAt, tz);
  const safeReason = sanitizePatientField(dto.reason);
  const isTeleconsult = dto.location?.trim().startsWith("http");
  return {
    day: parts.day,
    month: parts.month,
    weekday: parts.weekday,
    doctorName: dto.doctorName,
    specialty: dto.specialty,
    department: dto.specialty,
    location: isTeleconsult ? "Teleconsultation" : (dto.location ?? "Clinic"),
    visitType: safeReason ?? "Consultation",
    reason: safeReason ?? null,
    status: toPatientStatus(dto.status),
    startsAt: dto.startsAt,
    endsAt: dto.endsAt,
    doctorId: dto.doctorId,
    cancellationPolicy: dto.status === "BOOKED" ? CANCELLATION_POLICY : null,
    meetingLink: isTeleconsult ? (dto.location ?? null) : null,
    time: formatPatientTime(dto.startsAt, tz),
    timeRemaining: formatTimeRemaining(dto.startsAt, Date.now(), tz),
  };
}

/**
 * Builds the dashboard view-model from the freshly fetched clinical data. All
 * date/time presentation is rendered in the patient's own timezone; the
 * "New results" count, the activity feed, and the stat card all derive from the
 * same `reports` list so dashboard counts, reminders and lab-report list never
 * disagree within a single load.
 */
export function buildPatientDashboardLiveData(
  appointments: AppointmentDto[],
  prescriptions: PrescriptionDto[],
  reports: ClinicalReportDto[],
  invoices: InvoiceDto[] = [],
  profile: PatientProfile | null = null,
): PatientDashboardLiveData {
  const now = Date.now();
  const tz = patientTimeZone();
  const bookedFuture = appointments
    .filter((item) => item.status === "BOOKED" && new Date(item.startsAt).getTime() > now)
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const next = bookedFuture[0] ?? null;

  const activeRx = prescriptions.filter((item) => item.status === "ACTIVE");
  const refillsNeeded = activeRx.filter((item) => item.refillsRemaining === 0);
  const newReports = reports.filter((item) => item.status === "NEW");
  const latestReport = reports.reduce<ClinicalReportDto | null>((latest, r) => {
    if (!latest || new Date(r.createdAt).getTime() > new Date(latest.createdAt).getTime()) {
      return r;
    }
    return latest;
  }, null);

  const stats: DashboardStat[] = [
    {
      id: "next-appointment",
      label: "Next Appointment",
      value: next
        ? upcomingCardParts(next.startsAt, tz).weekday
        : "None",
      sublabel: next
        ? `${next.doctorName} · ${formatTimeRemaining(next.startsAt, now, tz)}`
        : "No visit booked",
      linkLabel: "View details",
    },
    {
      id: "prescriptions",
      label: "Prescriptions",
      value: String(activeRx.length),
      sublabel:
        refillsNeeded.length > 0
          ? `${refillsNeeded.length} refill${refillsNeeded.length === 1 ? "" : "s"} due soon`
          : "No refills due",
      linkLabel: "View all",
    },
    {
      id: "lab-reports",
      label: "Lab Reports",
      value: String(newReports.length),
      sublabel:
        newReports.length > 0
          ? `${newReports.length} new, ${newReports.length} unread`
          : "All reviewed",
      linkLabel: "View reports",
    },
    {
      id: "medical-records",
      label: "Medical Records",
      value: String(reports.length),
      sublabel: latestReport
        ? `Recently added: ${formatPatientDateTimeAbsolute(latestReport.createdAt, tz)}`
        : "No documents on file",
      linkLabel: "View all",
    },
  ];

  const billingStat = buildBillingStat(invoices, profile, tz);
  if (billingStat) {
    stats.push(billingStat);
  }

  return {
    stats,
    upcomingAppointment: next ? toUpcomingCard(next) : null,
    recentAppointments: appointments
      .slice()
      .sort((a, b) => b.startsAt.localeCompare(a.startsAt))
      .slice(0, 5)
      .map((dto) => {
        // Recent rows reuse the patient-formatted date for consistency with the
        // appointment list and detail pages.
        return {
          ...toRecentRecord(dto),
        };
      }),
    activity: buildActivity(next, prescriptions, reports, tz),
    healthMetrics: buildHealthMetrics(prescriptions, reports, tz),
  };
}

function toRecentRecord(dto: AppointmentDto): RecentAppointmentRow {
  const safeReason = sanitizePatientField(dto.reason);
  return {
    id: dto.id,
    doctorId: dto.doctorId,
    startsAt: dto.startsAt,
    endsAt: dto.endsAt,
    dateTime: formatPatientDateTime(dto.startsAt, patientTimeZone()),
    doctorName: dto.doctorName,
    department: dto.specialty,
    status: toPatientStatus(dto.status),
    location: dto.location ?? undefined,
    reason: safeReason ?? undefined,
    visitType: safeReason ?? "Consultation",
  };
}

function buildBillingStat(
  invoices: InvoiceDto[],
  profile: PatientProfile | null,
  _tz: string,
): DashboardStat | null {
  const balanceCents = invoices.reduce(
    (sum, inv) => sum + Math.max(inv.balanceCents, 0),
    0,
  );
  const dueCount = invoices.filter(
    (inv) => inv.status === "ISSUED" || inv.status === "PARTIALLY_PAID",
  ).length;
  const insuranceAction =
    profile &&
    (!profile.insuranceProvider || !profile.insurancePolicyNumber);
  const needsAttention = balanceCents > 0 || dueCount > 0 || insuranceAction;
  if (!needsAttention) {
    return null;
  }
  const value = balanceCents > 0 ? formatCents(balanceCents) : `${dueCount} due`;
  const sublabel =
    balanceCents > 0
      ? `${dueCount} invoice${dueCount === 1 ? "" : "s"} due`
      : insuranceAction
        ? "Insurance action needed"
        : `${dueCount} invoice${dueCount === 1 ? "" : "s"} due`;
  return {
    id: "billing",
    label: "Billing & Payments",
    value,
    sublabel,
    linkLabel: "View billing",
  };
}

function buildActivity(
  next: AppointmentDto | null,
  prescriptions: PrescriptionDto[],
  reports: ClinicalReportDto[],
  tz: string,
): NotificationItem[] {
  const items: NotificationItem[] = [];
  if (next) {
    items.push({
      id: `apt-${next.id}`,
      title: "Upcoming visit",
      description: `${next.doctorName} · ${formatPatientDateTime(next.startsAt, tz)}`,
      timeAgo: relativePart(next.startsAt),
      timeAbsolute: formatPatientDateTimeAbsolute(next.startsAt, tz),
      category: "appointment",
      actionLabel: "View Appointment",
      actionPath: "/patient/appointments",
      unread: true,
    });
  }
  for (const report of reports.filter((item) => item.status === "NEW").slice(0, 3)) {
    items.push({
      id: `rep-${report.id}`,
      title: "New lab result",
      description: `${report.title} · ${report.doctorName}`,
      timeAgo: relativePart(report.createdAt),
      timeAbsolute: formatPatientDateTimeAbsolute(report.createdAt, tz),
      category: "lab",
      actionLabel: "View Report",
      actionPath: "/patient/labs",
      unread: true,
    });
  }
  for (const rx of prescriptions.filter((item) => item.status === "ACTIVE").slice(0, 2)) {
    const needsRefill = rx.refillsRemaining === 0;
    items.push({
      id: `rx-${rx.id}`,
      title: needsRefill ? "Refill needed" : "Prescription on file",
      description: `${rx.medicationName} · ${rx.doctorName}`,
      timeAgo: relativePart(rx.createdAt),
      timeAbsolute: formatPatientDateTimeAbsolute(rx.createdAt, tz),
      category: "prescription",
      actionLabel: needsRefill ? "Request Refill" : "View Prescription",
      actionPath: "/patient/prescriptions",
      unread: !needsRefill,
    });
  }
  return items.slice(0, 6);
}

function buildHealthMetrics(
  prescriptions: PrescriptionDto[],
  reports: ClinicalReportDto[],
  tz: string,
): HealthMetric[] {
  const activeMeds = prescriptions.filter((item) => item.status === "ACTIVE");
  const newReports = reports.filter((item) => item.status === "NEW");
  const metrics: HealthMetric[] = [];
  metrics.push({
    id: "active-meds",
    label: "Active medications",
    value: String(activeMeds.length),
    status: activeMeds.length > 0 ? "ok" : "empty",
  });
  metrics.push({
    id: "new-labs",
    label: "New lab results",
    value:
      newReports.length > 0
        ? `${newReports.length} unread`
        : newReports.length === 0 && reports.length > 0
          ? "None new"
          : "None",
    status: newReports.length > 0 ? "alert" : "ok",
  });
  if (reports.length > 0) {
    const latest = reports.reduce((a, b) =>
      new Date(a.createdAt).getTime() > new Date(b.createdAt).getTime() ? a : b,
    );
    metrics.push({
      id: "latest-report",
      label: "Most recent report",
      value: formatPatientDateTimeAbsolute(latest.createdAt, tz),
      status: "info",
    });
  }
  return metrics;
}
