import type { ApiResponse } from "../types/api";
import type { AuthUser, LoginRequest, RegisterDoctorRequest, RegisterPatientRequest } from "../types/auth";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

export async function login(request: LoginRequest): Promise<AuthUser> {
  try {
    const response = await api.post<ApiResponse<AuthUser>>("/auth/login", request);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to sign in. Please try again."));
  }
}

export async function registerPatient(request: RegisterPatientRequest): Promise<AuthUser> {
  try {
    const response = await api.post<ApiResponse<AuthUser>>("/v1/patients", request);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to create your account. Please try again."));
  }
}

export async function registerDoctor(request: RegisterDoctorRequest): Promise<AuthUser> {
  try {
    const response = await api.post<ApiResponse<AuthUser>>("/v1/doctors", request);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to create your doctor account. Please try again."));
  }
}

export async function logout(): Promise<void> {
  await api.post("/auth/logout");
}
