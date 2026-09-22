import type { ApiResponse } from "../types/api";
import type { Role } from "../types/auth";
import type { DoctorProfile } from "../types/doctor";
import type { PatientProfile } from "../types/patient";
import { api } from "./api";
import { mapDoctorProfile, type DoctorProfileResponse } from "./doctorService";
import { mapPatientProfile, type PatientProfileResponse } from "./patientService";

/** Mirrors the backend {@code MeResponse}: identity plus the role-specific profile. */
interface MeResponse {
  email: string;
  role: Role;
  patientProfile: PatientProfileResponse | null;
  doctorProfile: DoctorProfileResponse | null;
}

/** Session identity plus the profile resolved for the signed-in role. */
export interface SessionBootstrap {
  email: string;
  role: Role;
  patientProfile: PatientProfile | null;
  doctorProfile: DoctorProfile | null;
}

/**
 * Restores the session in one request. Replaces the serial
 * /auth/me -> /v1/{patients|doctors}/me pair the provider used to make on boot, where
 * the profile request could not start until the identity response named the role. The
 * backend treats the profile as best-effort, so a null profile is expected when that
 * lookup failed server-side; the portal layouts keep their dedicated fetch as the
 * fallback for sessions established by login/registration.
 */
export async function getSessionBootstrap(): Promise<SessionBootstrap> {
  const response = await api.get<ApiResponse<MeResponse>>("/v1/me");
  const data = response.data?.data;
  if (!data) {
    throw new Error("Session bootstrap returned no data.");
  }
  return {
    email: data.email,
    role: data.role,
    patientProfile: data.patientProfile ? mapPatientProfile(data.patientProfile) : null,
    doctorProfile: data.doctorProfile ? mapDoctorProfile(data.doctorProfile) : null,
  };
}
