import type { ApiResponse } from "../types/api";
import type { AppointmentRecord, AppointmentStatus } from "../types/patient";
import { formatClinicDateTime } from "../lib/clinicTime";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

export type ApiAppointmentStatus = "BOOKED" | "CANCELLED" | "COMPLETED";

export interface DoctorSummary {
  id: string;
  fullName: string;
  specialty: string;
  licenseNumber: string;
}

export interface AppointmentDto {
  id: string;
  patientId: string;
  doctorId: string;
  patientName: string;
  patientMrn: string;
  doctorName: string;
  specialty: string;
  startsAt: string;
  endsAt: string;
  status: ApiAppointmentStatus;
  reason: string | null;
  location: string | null;
}

interface AppointmentPageDto {
  items: AppointmentDto[];
  page: number;
  size: number;
  total: number;
}

function toUiStatus(status: ApiAppointmentStatus): AppointmentStatus {
  if (status === "BOOKED") {
    return "UPCOMING";
  }
  return status;
}

export function toAppointmentRecord(dto: AppointmentDto): AppointmentRecord {
  return {
    id: dto.id,
    doctorId: dto.doctorId,
    startsAt: dto.startsAt,
    dateTime: formatClinicDateTime(dto.startsAt),
    doctorName: dto.doctorName,
    department: dto.specialty,
    status: toUiStatus(dto.status),
    location: dto.location ?? undefined,
    reason: dto.reason ?? undefined,
  };
}

export async function listDoctors(specialty?: string): Promise<DoctorSummary[]> {
  try {
    const response = await api.get<ApiResponse<DoctorSummary[]>>("/v1/doctors", {
      params: specialty ? { specialty } : undefined,
    });
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load doctors. Please try again."));
  }
}

export async function listDoctorSlots(doctorId: string, date: string): Promise<string[]> {
  try {
    const response = await api.get<ApiResponse<{ slots: string[] }>>(`/v1/doctors/${doctorId}/slots`, {
      params: { date },
    });
    return response.data.data.slots;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load available times. Please try again."));
  }
}

export async function bookAppointment(input: {
  doctorId: string;
  startsAt: string;
  reason?: string;
}): Promise<AppointmentDto> {
  try {
    const response = await api.post<ApiResponse<AppointmentDto>>(
      "/v1/appointments",
      {
        doctorId: input.doctorId,
        startsAt: input.startsAt,
        reason: input.reason || undefined,
      },
      { headers: { "Idempotency-Key": crypto.randomUUID() } },
    );
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to book that appointment. Please try again."));
  }
}

export async function listMyAppointments(): Promise<AppointmentDto[]> {
  try {
    const response = await api.get<ApiResponse<AppointmentPageDto>>("/v1/appointments", {
      params: { page: 0, size: 50 },
    });
    return response.data.data.items;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load appointments. Please try again."));
  }
}

export async function listDoctorAppointments(options?: {
  from?: string;
  to?: string;
  status?: ApiAppointmentStatus;
}): Promise<AppointmentDto[]> {
  try {
    const response = await api.get<ApiResponse<AppointmentPageDto>>("/v1/appointments", {
      params: { ...options, page: 0, size: 50 },
    });
    return response.data.data.items;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load today's schedule. Please try again."));
  }
}

export async function cancelAppointment(appointmentId: string): Promise<AppointmentDto> {
  try {
    const response = await api.post<ApiResponse<AppointmentDto>>(`/v1/appointments/${appointmentId}/cancel`);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to cancel that appointment. Please try again."));
  }
}

export async function rescheduleAppointment(appointmentId: string, startsAt: string): Promise<AppointmentDto> {
  try {
    const response = await api.post<ApiResponse<AppointmentDto>>(
      `/v1/appointments/${appointmentId}/reschedule`,
      { startsAt },
    );
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to reschedule that appointment. Please try again."));
  }
}

export async function completeAppointment(appointmentId: string): Promise<AppointmentDto> {
  try {
    const response = await api.post<ApiResponse<AppointmentDto>>(`/v1/appointments/${appointmentId}/complete`);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to complete that visit. Please try again."));
  }
}
