import { CheckCircle, Clock, MapPin, Moon } from "lucide-react";

import type { ApiResponse } from "../types/api";
import type {
  DoctorDutyStatus,
  DoctorDutyStatusLog,
  DoctorProfile,
  DutyStatusOption,
} from "../types/doctor";
import type { AppointmentDto } from "./appointmentService";
import type { ClinicalReportDto } from "./clinicalService";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

const DUTY_STATUS_STORAGE_KEY = "medops:doctor:dutyStatus";
const DUTY_STATUS_LOG_KEY = "medops:doctor:dutyStatusLog";
const DEFAULT_DUTY_STATUS: DoctorDutyStatus = "ON_DUTY";

const DUTY_BADGE =
  "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold";

export const DUTY_STATUS_OPTIONS: Record<DoctorDutyStatus, DutyStatusOption> = {
  ON_DUTY: {
    value: "ON_DUTY",
    label: "On Duty",
    description: "Available for consultations and lab reviews",
    badge: `${DUTY_BADGE} bg-emerald-50 text-emerald-700`,
    icon: CheckCircle,
  },
  BUSY: {
    value: "BUSY",
    label: "Busy",
    description: "In a consultation, available for urgent items only",
    badge: `${DUTY_BADGE} bg-amber-50 text-amber-700`,
    icon: Clock,
  },
  AWAY: {
    value: "AWAY",
    label: "Away",
    description: "Stepped away, will respond when back",
    badge: `${DUTY_BADGE} bg-blue-50 text-blue-700`,
    icon: MapPin,
  },
  OFF_DUTY: {
    value: "OFF_DUTY",
    label: "Off Duty",
    description: "Signed off for the day",
    badge: `${DUTY_BADGE} bg-slate-100 text-slate-700`,
    icon: Moon,
  },
};

export function getDutyStatusLabel(status: DoctorDutyStatus): string {
  return DUTY_STATUS_OPTIONS[status].label;
}

function readStorage<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function writeStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage quota or private-mode — silently ignore; duty status is non-critical.
  }
}

export function getStoredDutyStatus(): DoctorDutyStatus {
  return readStorage<DoctorDutyStatus>(DUTY_STATUS_STORAGE_KEY) ?? DEFAULT_DUTY_STATUS;
}

export function saveDutyStatus(status: DoctorDutyStatus): void {
  writeStorage(DUTY_STATUS_STORAGE_KEY, status);
}

/** Reads the persisted audit trail for duty-status changes (client-side only). */
export function getDutyStatusAuditLog(doctorId: string): DoctorDutyStatusLog[] {
  const all = readStorage<Record<string, DoctorDutyStatusLog[]>>(DUTY_STATUS_LOG_KEY) ?? {};
  return all[doctorId] ?? [];
}

/** Appends a duty-status change to the client-side audit trail. */
export function appendDutyStatusLog(entry: DoctorDutyStatusLog): void {
  const all = readStorage<Record<string, DoctorDutyStatusLog[]>>(DUTY_STATUS_LOG_KEY) ?? {};
  const current = all[entry.doctorId] ?? [];
  current.push(entry);
  // Cap at 100 entries per doctor so the audit trail never grows unbounded.
  if (current.length > 100) {
    all[entry.doctorId] = current.slice(-100);
  } else {
    all[entry.doctorId] = current;
  }
  writeStorage(DUTY_STATUS_LOG_KEY, all);
}

export interface DoctorProfileResponse {
  email: string;
  fullName: string;
  specialty: string;
  licenseNumber: string;
  phoneNumber: string;
}

/** Maps the API profile payload onto the UI shape; shared with the /v1/me bootstrap. */
export function mapDoctorProfile(data: DoctorProfileResponse): DoctorProfile {
  return {
    name: data.fullName,
    specialty: data.specialty,
    licenseNumber: data.licenseNumber,
    email: data.email,
    phoneNumber: data.phoneNumber,
  };
}

/** Aggregated dashboard payload from GET /v1/doctors/me/dashboard. */
export interface DoctorDashboard {
  todayScheduleCount: number;
  completedTodayCount: number;
  awaitingTodayCount: number;
  pendingLabReportsCount: number;
  todayAppointments: AppointmentDto[];
  upcomingAppointments: AppointmentDto[];
  pendingLabReports: ClinicalReportDto[];
  generatedAt: string;
}

export async function getMyDoctorProfile(): Promise<DoctorProfile> {
  try {
    const response = await api.get<ApiResponse<DoctorProfileResponse>>("/v1/doctors/me");
    return mapDoctorProfile(response.data.data);
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load your profile. Please try again."));
  }
}

export async function getMyDashboard(): Promise<DoctorDashboard> {
  try {
    const response = await api.get<ApiResponse<DoctorDashboard>>("/v1/doctors/me/dashboard");
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load your dashboard. Please try again."));
  }
}
