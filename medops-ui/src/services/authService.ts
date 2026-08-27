import axios from "axios";

import type { ApiResponse, ErrorResponse } from "../types/api";
import type { AuthTokens, LoginRequest, RegisterDoctorRequest, RegisterPatientRequest } from "../types/auth";
import { api } from "./api";

export async function login(request: LoginRequest): Promise<AuthTokens> {
  try {
    const response = await api.post<ApiResponse<AuthTokens>>("/auth/login", request);
    return response.data.data;
  } catch (error) {
    if (axios.isAxiosError<ErrorResponse>(error) && error.response) {
      throw new Error(error.response.data.error.message);
    }
    throw new Error("Unable to reach the server. Please try again.");
  }
}

export async function registerPatient(request: RegisterPatientRequest): Promise<AuthTokens> {
  try {
    const response = await api.post<ApiResponse<AuthTokens>>("/v1/patients", request);
    return response.data.data;
  } catch (error) {
    if (axios.isAxiosError<ErrorResponse>(error) && error.response) {
      throw new Error(error.response.data.error.message);
    }
    throw new Error("Unable to reach the server. Please try again.");
  }
}

export async function registerDoctor(request: RegisterDoctorRequest): Promise<AuthTokens> {
  try {
    const response = await api.post<ApiResponse<AuthTokens>>("/v1/doctors", request);
    return response.data.data;
  } catch (error) {
    if (axios.isAxiosError<ErrorResponse>(error) && error.response) {
      throw new Error(error.response.data.error.message);
    }
    throw new Error("Unable to reach the server. Please try again.");
  }
}

export async function logout(refreshToken: string): Promise<void> {
  await api.post("/auth/logout", { refreshToken });
}
