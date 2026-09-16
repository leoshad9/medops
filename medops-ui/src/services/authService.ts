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

/** Starts a password-recovery flow for an email address. */
export async function forgotPassword(email: string): Promise<{ resetFlowId: string | null; message: string }> {
  try {
    const response = await api.post<ApiResponse<{ message: string; resetFlowId: string | null }>>("/auth/password/forgot", { email });
    return { resetFlowId: response.data.data.resetFlowId ?? null, message: response.data.data.message };
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to send OTP. Please try again."));
  }
}

/** Requests a replacement OTP for an existing recovery flow. */
export async function resendOtp(resetFlowId: string): Promise<ResendOtpResponse> {
  try {
    const response = await api.post<ApiResponse<ResendOtpResponse>>("/auth/password/resend-otp", { resetFlowId });
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to resend OTP. Please try again."));
  }
}

export interface ResendOtpResponse {
  status: "SENT" | "EXPIRED" | "COOLDOWN";
  message: string;
}

// The API returns the reset token in an HttpOnly cookie, so nothing here ever sees it.
/** Verifies a recovery OTP and establishes reset authorization. */
export async function verifyOtp(resetFlowId: string, otp: string): Promise<void> {
  try {
    await api.post("/auth/password/verify-otp", { resetFlowId, otp });
  } catch (error) {
    throw new Error(messageFromApiError(error, "Invalid OTP. Please try again."));
  }
}

/** Replaces the password authorized by the verified reset cookie. */
export async function resetPassword(newPassword: string): Promise<void> {
  try {
    await api.post("/auth/password/reset", { newPassword });
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to reset password. Please try again."));
  }
}
