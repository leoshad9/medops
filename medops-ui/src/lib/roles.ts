import type { Role } from "../types/auth";

const DASHBOARD_PATHS: Record<Role, string> = {
  DOCTOR: "/doctor/dashboard",
  PATIENT: "/patient/dashboard",
};

export function roleDashboardPath(role: Role): string {
  return DASHBOARD_PATHS[role];
}
