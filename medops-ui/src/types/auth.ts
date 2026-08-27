export type Role = "DOCTOR" | "PATIENT";

// Derived client-side from the access token's "sub"/"roles" claims — the
// login response itself carries only tokens, no user info (see AuthTokens).
export interface AuthUser {
  email: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export type Gender = "MALE" | "FEMALE" | "OTHER";

export interface RegisterPatientRequest {
  email: string;
  password: string;
  fullName: string;
  dateOfBirth: string; // ISO date (yyyy-MM-dd)
  gender: Gender;
  phoneNumber: string;
}

export interface RegisterDoctorRequest {
  email: string;
  password: string;
  fullName: string;
  specialty: string;
  licenseNumber: string;
  phoneNumber: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}
