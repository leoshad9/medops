import { FlaskConical, Pill, Clock, Users } from "lucide-react";

import { calcAge, formatClinicTime } from "./clinicTime";
import { doctorPatientChartPath, DOCTOR_PATHS } from "./doctorRoutes";
import type { AppointmentDto } from "../services/appointmentService";
import type { ClinicalReportDto, PrescriptionDto } from "../services/clinicalService";
import type { NotificationItem } from "../types/patient";
import type { DoctorDashboard } from "../services/doctorService";
import type {
  ClinicalAlert,
  ClinicalAppointmentStatus,
  DoctorDashboardStat,
  PrescriptionTask,
  ScheduleFilter,
  TodayAppointment,
} from "../types/doctor";

/**
 * Maps the persisted API appointment status onto the clinical display status used
 * by the dashboard table and cards: anything still BOOKED is a CONFIRMED visit for
 * the clinician; completed visits are surfaced as COMPLETED; cancelled visits
 * are surfaced as CANCELLED so they can appear in the Cancelled filter tab.
 */
export function toTodayRow(dto: AppointmentDto): TodayAppointment {
  const status: ClinicalAppointmentStatus =
    dto.status === "COMPLETED"
      ? "COMPLETED"
      : dto.status === "CANCELLED"
        ? "CANCELLED"
        : "CONFIRMED";
  const isTele =
    dto.location?.toLowerCase().includes("tele") ||
    dto.location?.toLowerCase().includes("virtual") ||
    dto.location?.toLowerCase().includes("video");
  return {
    id: dto.id,
    patientId: dto.patientId,
    time: formatClinicTime(dto.startsAt),
    patientName: dto.patientName,
    patientMrn: dto.patientMrn,
    age: calcAge(dto.patientDateOfBirth),
    gender: dto.patientGender,
    reason: dto.reason ?? "—",
    type: isTele ? "Teleconsultation" : "Clinic Visit",
    location: dto.location ?? "General OPD",
    status,
  };
}

/**
 * Builds a list of cancelled appointments for today by querying the API with
 * the CANCELLED status filter and converting each to a TodayAppointment row.
 */
export async function fetchCancelledTodayRows(
  fromIso: string,
  toIso: string,
): Promise<TodayAppointment[]> {
  const { listDoctorAppointments } = await import("../services/appointmentService");
  const cancelled = await listDoctorAppointments({ from: fromIso, to: toIso, status: "CANCELLED" });
  return cancelled.map((dto): TodayAppointment => {
    const isTele = dto.location?.toLowerCase().includes("tele");
    return {
      id: dto.id,
      patientId: dto.patientId,
      time: formatClinicTime(dto.startsAt),
      patientName: dto.patientName,
      patientMrn: dto.patientMrn,
      age: calcAge(dto.patientDateOfBirth),
      gender: dto.patientGender,
      reason: dto.reason ?? "—",
      type: isTele ? "Teleconsultation" : "Clinic Visit",
      location: dto.location ?? "General OPD",
      status: "CANCELLED",
    };
  });
}

/**
 * The next patient the doctor needs to see: the first non-completed
 * appointment of the day, in time order. `null` when the day is clear.
 */
export function deriveNextAppointment(appointments: TodayAppointment[]): TodayAppointment | null {
  return appointments.find((apt) => apt.status === "CONFIRMED") ?? null;
}

const ABNORMAL_PATTERN =
  /critical|abnormal|panic|severe|life-?threat|elevated|stat|life.?threatening/i;

/**
 * Surfaces pending (NEW) lab reports as clinical alerts, sorted by urgency.
 * Reports whose title, summary, or notes look abnormal are escalated to
 * CRITICAL; others are WARNINGs that still need a review acknowledgement.
 */
export function deriveUrgentLabAlerts(labs: ClinicalReportDto[]): ClinicalAlert[] {
  return labs
    .filter((lab) => lab.status === "NEW")
    .map((lab): ClinicalAlert => {
      const haystack = `${lab.title} ${lab.summary ?? ""} ${lab.notes ?? ""}`;
      const isCritical = ABNORMAL_PATTERN.test(haystack);
      const title = lab.summary
        ? `${lab.title} — ${truncate(lab.summary, 140)}`
        : lab.title;
      return {
        id: `lab-${lab.id}`,
        severity: isCritical ? "CRITICAL" : "WARNING",
        category: "LAB",
        title: isCritical ? `Critical result: ${title}` : `Review needed: ${title}`,
        detail: lab.summary
          ? `${lab.patientName} — ${truncate(lab.summary, 140)}`
          : `Lab report awaiting review for ${lab.patientName}.`,
        patientName: lab.patientName,
        patientMrn: lab.patientMrn,
        referenceId: lab.id,
        createdAt: lab.createdAt,
        actionLabel: isCritical ? "Review result" : "Review",
        actionPath: doctorPatientChartPath(lab.patientId),
      };
    });
}

/**
 * Unread notifications become actionable alerts so critical messages are not
 * buried in the bell dropdown. Capped to keep the workspace focused.
 */
export function deriveNotificationAlerts(
  notifications: NotificationItem[],
): ClinicalAlert[] {
  return notifications
    .filter((n) => n.unread)
    .slice(0, 5)
    .map(
      (n): ClinicalAlert => ({
        id: `notif-${n.id}`,
        severity: "WARNING",
        category: "NOTIFICATION",
        title: n.title,
        detail: n.description,
        createdAt: "",
        referenceId: n.id,
        actionLabel: "View",
        actionPath: DOCTOR_PATHS.notifications,
      }),
    );
}

/**
 * Combines urgent lab alerts and high-priority notification alerts into a single
 * list sorted by clinical urgency (CRITICAL first), then by creation time.
 */
export function deriveUrgentAlerts(
  labs: ClinicalReportDto[],
  notifications: NotificationItem[],
): ClinicalAlert[] {
  const alerts = [...deriveUrgentLabAlerts(labs), ...deriveNotificationAlerts(notifications)];
  alerts.sort((a, b) => {
    const severityOrder: Record<ClinicalAlert["severity"], number> = {
      CRITICAL: 0,
      ABNORMAL: 1,
      WARNING: 2,
    };
    const diff = severityOrder[a.severity] - severityOrder[b.severity];
    if (diff !== 0) return diff;
    return new Date(a.createdAt || Date.now()).getTime() - new Date(b.createdAt || Date.now()).getTime();
  });
  return alerts;
}

function truncate(text: string, max: number): string {
  return text.length <= max ? text : `${text.slice(0, max - 1)}…`;
}

/**
 * Prescriptions that need the doctor's hand: active drugs with no refills left
 * (renewal due). Other buckets (FAILED_TRANSMISSION, DRAFT) would be sourced
 * from a future dispensing/esign status API; refills-due is the only signal the
 * current API exposes today.
 */
export function derivePrescriptionTasks(prescriptions: PrescriptionDto[]): PrescriptionTask[] {
  return prescriptions
    .filter((rx) => rx.status === "ACTIVE" && rx.refillsRemaining === 0)
    .map(
      (rx): PrescriptionTask => ({
        id: rx.id,
        medicationName: rx.medicationName,
        patientName: rx.patientName,
        patientMrn: rx.patientMrn,
        patientId: rx.patientId,
        bucket: "RENEWAL",
        createdAt: rx.createdAt,
        chartPath: doctorPatientChartPath(rx.patientId),
      }),
    );
}

export function countPrescriptionTasks(prescriptions: PrescriptionDto[]): number {
  return derivePrescriptionTasks(prescriptions).length;
}

 /**
 * Builds the consultation queue: today's CONFIRMED (booked, not yet completed)
 * appointments ordered by scheduled time, ready for the clinician to call in.
 */
export function buildConsultationQueue(appointments: TodayAppointment[]): TodayAppointment[] {
  return [...appointments]
    .filter((apt) => apt.status === "CONFIRMED")
    .sort((a, b) => {
      const atA = Date.parse(a.time);
      const atB = Date.parse(b.time);
      return atA - atB;
    });
}

/**
 * Filters today's appointment rows by the selected schedule filter, matching the
 * TodayScheduleTable filter tabs (Today/Upcoming show scheduled visits, Completed
 * and Cancelled show those respective statuses).
 */
export function filterAppointments(
  appointments: TodayAppointment[],
  filter: ScheduleFilter,
): TodayAppointment[] {
  switch (filter) {
    case "completed":
      return appointments.filter((a) => a.status === "COMPLETED");
    case "cancelled":
      return appointments.filter((a) => a.status === "CANCELLED");
    case "today":
    case "upcoming":
    default:
      return appointments.filter((a) => a.status === "CONFIRMED");
  }
}

/**
 * Items surfaced in the "Needs Attention" area before the stat cards: critical
 * and abnormal lab results, unread high-priority notifications. Sorted by
 * clinical urgency, then by due time.
 */
export function buildNeedsAttention(
  labs: ClinicalReportDto[],
  notifications: NotificationItem[],
): ClinicalAlert[] {
  return deriveUrgentAlerts(labs, notifications).slice(0, 8);
}

/**
 * Pending clinical items used to gate Off Duty transitions — labs awaiting
 * review and patients awaiting consultation. Returned as short human-readable
 * strings shown in the confirmation overlay.
 */
export function buildPendingClinicalWorkItems(dashboard: DoctorDashboard): string[] {
  const items: string[] = [];
  if (dashboard.awaitingTodayCount > 0) {
    items.push(`${dashboard.awaitingTodayCount} patients awaiting consultation`);
  }
  if (dashboard.pendingLabReportsCount > 0) {
    items.push(`${dashboard.pendingLabReportsCount} lab reports pending review`);
  }
  return items;
}

/**
 * KPI cards derived from the same aggregate payload that feeds the schedule,
 * labs, and prescription panels, so the numbers always reconcile with what the
 * clinician sees when they open the detailed pages. Each card links to its
 * filtered detail view.
 */
export function buildStats(
  dashboard: DoctorDashboard,
  prescriptionTaskCount: number,
): DoctorDashboardStat[] {
  const booked = dashboard.awaitingTodayCount;
  const completed = dashboard.completedTodayCount;

  const criticalLabs = dashboard.pendingLabReports.filter((lab) => {
    const haystack = `${lab.title} ${lab.summary ?? ""} ${lab.notes ?? ""}`.toLowerCase();
    return /critical|abnormal|panic|severe|life.?threat|elevated|stat/i.test(haystack);
  }).length;
  const routineLabs = dashboard.pendingLabReportsCount - criticalLabs;

  const longestWait = (() => {
    const now = Date.now();
    const waiting = dashboard.todayAppointments
      .filter((a) => a.status === "BOOKED")
      .map((a) => new Date(a.startsAt).getTime())
      .filter((t) => t < now);
    if (waiting.length === 0) return 0;
    const oldest = Math.min(...waiting);
    return Math.floor((now - oldest) / 60000);
  })();

  return [
    {
      id: "today-schedule",
      label: "Today's Schedule",
      value: `${dashboard.todayScheduleCount} Patients`,
      sublabel: `${booked} booked · ${completed} completed · ${dashboard.awaitingTodayCount} awaiting · 0 cancelled`,
      icon: Users,
      to: DOCTOR_PATHS.appointments,
    },
    {
      id: "awaiting-consultation",
      label: "Awaiting Consultation",
      value: `${dashboard.awaitingTodayCount} Patients`,
      sublabel: `${dashboard.awaitingTodayCount} waiting · ${longestWait} min longest wait`,
      icon: Clock,
      to: DOCTOR_PATHS.appointments,
    },
    {
      id: "pending-labs",
      label: "Pending Lab Reviews",
      value: `${dashboard.pendingLabReportsCount} Reports`,
      sublabel: `${criticalLabs} critical · ${routineLabs} routine`,
      icon: FlaskConical,
      to: DOCTOR_PATHS.labs,
      trend: dashboard.pendingLabReportsCount > 0 ? "Action needed" : "All clear",
      trendPositive: dashboard.pendingLabReportsCount === 0,
    },
    {
      id: "prescription-tasks",
      label: "Prescription Tasks",
      value: `${prescriptionTaskCount} Tasks`,
      sublabel: prescriptionTaskCount > 0 ? `${prescriptionTaskCount} renewal${prescriptionTaskCount > 1 ? "s" : ""} due` : "No drafts or renewals",
      icon: Pill,
      to: DOCTOR_PATHS.prescriptions,
      trend: prescriptionTaskCount > 0 ? "Action needed" : undefined,
      trendPositive: prescriptionTaskCount === 0,
    },
  ];
}
