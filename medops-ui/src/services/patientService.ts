import type { ApiResponse } from "../types/api";
import type { PatientProfile } from "../types/patient";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

interface PatientProfileResponse {
  id: string;
  email: string;
  fullName: string;
  mrn: string;
  dateOfBirth: string;
  gender: string;
  phoneNumber: string;
}

export async function getMyProfile(): Promise<PatientProfile & { id: string }> {
  try {
    const response = await api.get<ApiResponse<PatientProfileResponse>>("/v1/patients/me");
    const d = response.data.data;
    return { id: d.id, name: d.fullName, mrn: d.mrn };
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load your profile. Please try again."));
  }
}
