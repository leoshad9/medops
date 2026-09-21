export type DoctorViewKey =
  | "dashboard"
  | "appointments"
  | "patients"
  | "prescriptions"
  | "labs"
  | "notifications"
  | "profile";

export const DOCTOR_PATHS: Record<DoctorViewKey, string> = {
  dashboard: "/doctor/dashboard",
  appointments: "/doctor/appointments",
  patients: "/doctor/patients",
  prescriptions: "/doctor/prescriptions",
  labs: "/doctor/labs",
  notifications: "/doctor/notifications",
  profile: "/doctor/profile",
};

export const DOCTOR_VIEW_METADATA: Record<DoctorViewKey, { title?: string; subtitle?: string }> = {
  dashboard: {},
  appointments: {
    title: "Appointments",
    subtitle: "Today's roster and upcoming booked visits.",
  },
  patients: {
    title: "Patient Roster",
    subtitle: "Patients you have booked or completed visits with.",
  },
  prescriptions: {
    title: "E-Prescriptions",
    subtitle: "Prescriptions you have written for your patients.",
  },
  labs: {
    title: "Diagnostic & Labs",
    subtitle: "Reports you have uploaded for your patients.",
  },
  notifications: {
    title: "Notifications",
    subtitle: "Everything we've sent you — read and unread.",
  },
  profile: {
    title: "My Profile",
    subtitle: "Your verified clinical identity on file with MedOps.",
  },
};

export function doctorViewFromPath(pathname: string): DoctorViewKey {
  if (pathname.startsWith("/doctor/appointments")) {
    return "appointments";
  }
  if (pathname.startsWith("/doctor/patients")) {
    return "patients";
  }
  if (pathname.startsWith("/doctor/prescriptions")) {
    return "prescriptions";
  }
  if (pathname.startsWith("/doctor/labs")) {
    return "labs";
  }
  if (pathname.startsWith("/doctor/notifications")) {
    return "notifications";
  }
  if (pathname.startsWith("/doctor/profile")) {
    return "profile";
  }
  return "dashboard";
}

export function doctorPatientChartPath(patientId: string): string {
  return `/doctor/patients/${patientId}`;
}
