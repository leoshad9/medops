import axios from "axios";

import type { ApiResponse, ErrorResponse } from "../types/api";
import type { DoctorProfile } from "../types/doctor";
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
    if (axios.isAxiosError<ErrorResponse>(error) && error.response) {
      throw new Error(error.response.data.error.message);
    }
    throw new Error("Unable to reach the server. Please try again.");
  }
}
