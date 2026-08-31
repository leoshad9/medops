export type DoctorViewKey = "dashboard" | "appointments" | "patients" | "prescriptions" | "labs";

export const DOCTOR_PATHS: Record<DoctorViewKey, string> = {
  dashboard: "/doctor/dashboard",
  appointments: "/doctor/appointments",
  patients: "/doctor/patients",
  prescriptions: "/doctor/prescriptions",
  labs: "/doctor/labs",
};

export const DOCTOR_VIEW_METADATA: Record<DoctorViewKey, { title?: string; subtitle?: string }> = {
  dashboard: {},
  appointments: {
    title: "Appointments",
    subtitle: "Today's roster and upcoming booked visits.",
  },
  patients: {
    title: "Patient roster",
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
  return "dashboard";
}

export function doctorPatientChartPath(patientId: string): string {
  return `/doctor/patients/${patientId}`;
}
