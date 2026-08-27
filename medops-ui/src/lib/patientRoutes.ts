export type PatientViewKey =
  | "dashboard"
  | "appointments"
  | "book"
  | "prescriptions"
  | "labs"
  | "records"
  | "billing"
  | "profile"
  | "help";

export const PATIENT_PATHS: Record<PatientViewKey, string> = {
  dashboard: "/patient/dashboard",
  appointments: "/patient/appointments",
  book: "/patient/book",
  prescriptions: "/patient/prescriptions",
  labs: "/patient/labs",
  records: "/patient/records",
  billing: "/patient/billing",
  profile: "/patient/profile",
  help: "/patient/help",
};

export const PATIENT_VIEW_METADATA: Record<PatientViewKey, { title?: string; subtitle?: string }> = {
  dashboard: {},
  appointments: {
    title: "Appointments",
    subtitle: "All your upcoming and past visits in one place.",
  },
  book: {
    title: "Book an Appointment",
    subtitle: "Choose a department, doctor, and time that works for you.",
  },
  prescriptions: {
    title: "Prescriptions",
    subtitle: "Your active and past prescriptions.",
  },
  labs: {
    title: "Lab Reports",
    subtitle: "Test results ordered by your care team.",
  },
  records: {
    title: "Medical Records",
    subtitle: "Documents and history from your visits.",
  },
  billing: {
    title: "Billing & Payments",
    subtitle: "Invoices, payments, and insurance details.",
  },
  profile: {
    title: "My Profile",
    subtitle: "Your personal and medical information.",
  },
  help: {
    title: "Help & Support",
    subtitle: "Answers to common questions, and ways to reach us.",
  },
};

export function patientViewFromPath(pathname: string): PatientViewKey {
  const segment = pathname.replace(/^\/patient\/?/, "").split("/")[0];
  if (segment && segment in PATIENT_PATHS) {
    return segment as PatientViewKey;
  }
  return "dashboard";
}
