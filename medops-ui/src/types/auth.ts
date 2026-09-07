export type Role = "DOCTOR" | "PATIENT";

// The login response carries the authenticated user's identity. The access token
// is an HttpOnly cookie set by the backend, so the client cannot read its claims —
// this type is the only source of truth for who is signed in.
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
