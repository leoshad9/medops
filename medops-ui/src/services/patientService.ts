import type { ApiResponse } from "../types/api";
import type { PatientProfile } from "../types/patient";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

interface PatientProfileResponse {
  email: string;
  fullName: string;
  mrn: string;
  dateOfBirth: string;
  gender: string;
  phoneNumber: string;
}

export async function getMyProfile(): Promise<PatientProfile> {
  try {
    const response = await api.get<ApiResponse<PatientProfileResponse>>("/v1/patients/me");
    return { name: response.data.data.fullName, mrn: response.data.data.mrn };
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load your profile. Please try again."));
  }
}
