import type { ApiResponse } from "../types/api";
import type { DoctorProfile } from "../types/doctor";
import type { AppointmentDto } from "./appointmentService";
import type { ClinicalReportDto } from "./clinicalService";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

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
