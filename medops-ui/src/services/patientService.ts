import axios from "axios";

import type { ApiResponse, ErrorResponse } from "../types/api";
import type { PatientProfile } from "../types/patient";
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
    if (axios.isAxiosError<ErrorResponse>(error) && error.response) {
      throw new Error(error.response.data.error.message);
    }
    throw new Error("Unable to reach the server. Please try again.");
  }
}
