export interface DoctorProfile {
  name: string;
  specialty: string;
  licenseNumber: string;
  email: string;
  phoneNumber: string;
}

export interface DoctorDashboardStat {
  id: string;
  label: string;
  value: string;
  sublabel: string;
  trend?: string;
  trendPositive?: boolean;
}

export type ClinicalAppointmentStatus = "CONFIRMED" | "COMPLETED";

export interface TodayAppointment {
  id: string;
  time: string;
  patientName: string;
  patientMrn: string;
  age: number | null;
  gender: string | null;
  reason: string;
  type: string;
  status: ClinicalAppointmentStatus;
}
