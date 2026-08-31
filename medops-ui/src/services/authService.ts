import type { ApiResponse } from "../types/api";
import type { AuthTokens, LoginRequest, RegisterDoctorRequest, RegisterPatientRequest } from "../types/auth";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

export async function login(request: LoginRequest): Promise<AuthTokens> {
  try {
    const response = await api.post<ApiResponse<AuthTokens>>("/auth/login", request);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to sign in. Please try again."));
  }
}

export async function registerPatient(request: RegisterPatientRequest): Promise<AuthTokens> {
  try {
    const response = await api.post<ApiResponse<AuthTokens>>("/v1/patients", request);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to create your account. Please try again."));
  }
}

export async function registerDoctor(request: RegisterDoctorRequest): Promise<AuthTokens> {
  try {
    const response = await api.post<ApiResponse<AuthTokens>>("/v1/doctors", request);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to create your doctor account. Please try again."));
  }
}

export async function logout(refreshToken: string): Promise<void> {
  await api.post("/auth/logout", { refreshToken });
}
