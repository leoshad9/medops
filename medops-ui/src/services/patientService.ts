import type { ApiResponse } from "../types/api";
import type { PatientProfile } from "../types/patient";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

export interface PatientProfileResponse {
  id: string;
  email: string;
  fullName: string;
  mrn: string;
  dateOfBirth: string;
  gender: string;
  phoneNumber: string;
  bloodGroup: string | null;
  address: string | null;
  emergencyContact: string | null;
  insuranceProvider: string | null;
  insurancePolicyNumber: string | null;
}

export interface UpdatePatientProfilePayload {
  fullName: string;
  phoneNumber: string;
  bloodGroup: string;
  address: string;
  emergencyContact: string;
  insuranceProvider: string;
  insurancePolicyNumber: string;
}

/** Maps the API profile payload onto the UI shape; shared with the /v1/me bootstrap. */
export function mapPatientProfile(d: PatientProfileResponse): PatientProfile {
  return {
    id: d.id,
    name: d.fullName,
    email: d.email,
    mrn: d.mrn,
    dateOfBirth: d.dateOfBirth,
    gender: d.gender,
    phoneNumber: d.phoneNumber,
    bloodGroup: d.bloodGroup ?? undefined,
    address: d.address ?? undefined,
    emergencyContact: d.emergencyContact ?? undefined,
    insuranceProvider: d.insuranceProvider ?? undefined,
    insurancePolicyNumber: d.insurancePolicyNumber ?? undefined,
  };
}

export async function getMyProfile(): Promise<PatientProfile> {
  try {
    const response = await api.get<ApiResponse<PatientProfileResponse>>("/v1/patients/me");
    return mapPatientProfile(response.data.data);
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load your profile. Please try again."));
  }
}

export async function updateMyProfile(payload: UpdatePatientProfilePayload): Promise<PatientProfile> {
  try {
    const response = await api.put<ApiResponse<PatientProfileResponse>>("/v1/patients/me", payload);
    return mapPatientProfile(response.data.data);
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to save your profile. Please try again."));
  }
}
