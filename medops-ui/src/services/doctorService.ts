import type { ApiResponse } from "../types/api";
import type { DoctorProfile } from "../types/doctor";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

interface DoctorProfileResponse {
  email: string;
  fullName: string;
  specialty: string;
  licenseNumber: string;
  phoneNumber: string;
}

export async function getMyDoctorProfile(): Promise<DoctorProfile> {
  try {
    const response = await api.get<ApiResponse<DoctorProfileResponse>>("/v1/doctors/me");
    const data = response.data.data;
    return {
      name: data.fullName,
      specialty: data.specialty,
      licenseNumber: data.licenseNumber,
      email: data.email,
      phoneNumber: data.phoneNumber,
    };
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load your profile. Please try again."));
  }
}
