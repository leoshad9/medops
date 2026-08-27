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

export type ClinicalAppointmentStatus = "IN_PROGRESS" | "WAITING" | "CONFIRMED" | "COMPLETED";

export interface TodayAppointment {
  id: string;
  time: string;
  patientName: string;
  patientMrn: string;
  age: number;
  gender: string;
  reason: string;
  type: string;
  status: ClinicalAppointmentStatus;
}

export interface PatientQueueItem {
  id: string;
  patientName: string;
  roomNumber: string;
  checkInTime: string;
  chiefComplaint: string;
  priority: "NORMAL" | "HIGH" | "URGENT";
}

export interface PendingLabReview {
  id: string;
  patientName: string;
  testName: string;
  orderedDate: string;
  status: "CRITICAL" | "ABNORMAL" | "NORMAL";
}

export interface DoctorDashboardData {
  profile: DoctorProfile;
  unreadAlertsCount: number;
  stats: DoctorDashboardStat[];
  todayAppointments: TodayAppointment[];
  patientQueue: PatientQueueItem[];
  pendingLabReviews: PendingLabReview[];
}
