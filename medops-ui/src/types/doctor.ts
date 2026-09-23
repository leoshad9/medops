import type { ComponentType } from "react";

export interface DoctorProfile {
  name: string;
  specialty: string;
  licenseNumber: string;
  email: string;
  phoneNumber: string;
}

export interface DoctorDashboardStat {
  id: string;
  label: string;
  value: string;
  sublabel: string;
  trend?: string;
  trendPositive?: boolean;
  /** Icon rendered inside the card (kept on the stat so the card stays data-driven). */
  icon: ComponentType<{ className?: string }>;
  /** The stat card renders as a link to this dashboard route. */
  to: string;
}

export type ClinicalAppointmentStatus = "CONFIRMED" | "COMPLETED" | "CANCELLED";

export type ScheduleFilter = "today" | "upcoming" | "completed" | "cancelled";

export interface TodayAppointment {
  id: string;
  patientId: string;
  time: string;
  patientName: string;
  patientMrn: string;
  age: number | null;
  gender: string | null;
  reason: string;
  type: string;
  location: string;
  status: ClinicalAppointmentStatus;
}

/** Presence status for the duty-active control. */
export type DoctorDutyStatus = "ON_DUTY" | "BUSY" | "AWAY" | "OFF_DUTY";

/** Display label and styling for a duty status option. */
export interface DutyStatusOption {
  value: DoctorDutyStatus;
  label: string;
  description: string;
  badge: string;
  icon: ComponentType<{ className?: string }>;
}

/** Audit-log record for a duty-status change, persisted client-side until the
 *  backend exposes a dedicated endpoint. Mirrors the server-side AuditEvent shape
 *  so the payload can be POSTed once a `PUT /v1/doctors/me/duty` route exists. */
export interface DoctorDutyStatusLog {
  doctorId: string;
  previousStatus: DoctorDutyStatus;
  newStatus: DoctorDutyStatus;
  timestamp: string;
}

/** A single urgent clinical alert surfaced at the top of the dashboard. */
export interface ClinicalAlert {
  id: string;
  severity: "CRITICAL" | "ABNORMAL" | "WARNING";
  category: "LAB" | "PRESCRIPTION" | "NOTIFICATION" | "FOLLOWUP" | "ALLERGY";
  title: string;
  detail: string;
  patientName?: string;
  patientMrn?: string;
  referenceId?: string;
  createdAt: string;
  actionLabel: string;
  actionPath: string;
}

/** Prescription task bucket shown in the dashboard summary. */
export interface PrescriptionTask {
  id: string;
  patientId: string;
  medicationName: string;
  patientName: string;
  patientMrn: string;
  bucket: "DRAFT" | "PENDING_SIGNATURE" | "RENEWAL" | "FAILED_TRANSMISSION" | "RECENTLY_ISSUED";
  createdAt: string;
  chartPath: string;
}
