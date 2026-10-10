import type { Role } from "../types/auth";

const DASHBOARD_PATHS: Record<Role, string> = {
  DOCTOR: "/doctor/dashboard",
  PATIENT: "/patient/dashboard",
};

export function roleDashboardPath(role?: Role | null): string {
  if (role && DASHBOARD_PATHS[role]) {
    return DASHBOARD_PATHS[role];
  }
  return "/login";
}

