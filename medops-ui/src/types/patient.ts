// Shapes matching what future GET /api/patients/me (and related endpoints)
// should return. Pages currently render mock data conforming to these types,
// so swapping in real API calls later is a data-source change, not a rewrite.

export interface PatientProfile {
  name: string;
  mrn: string;
}

export interface DashboardStat {
  id: string;
  label: string;
  value: string;
  sublabel: string;
  linkLabel: string;
}

export interface UpcomingAppointment {
  day: string;
  month: string;
  weekday: string;
  doctorName: string;
  specialty: string;
  time: string;
  location: string;
  visitType: string;
}

export type AppointmentStatus = "COMPLETED" | "CANCELLED" | "UPCOMING";

export interface AppointmentRecord {
  id: string;
  doctorId?: string;
  startsAt?: string;
  dateTime: string;
  doctorName: string;
  department: string;
  status: AppointmentStatus;
  location?: string;
  reason?: string;
}

export type RecentAppointmentRow = AppointmentRecord;

export interface NotificationItem {
  id: string;
  title: string;
  description: string;
  timeAgo: string;
  unread?: boolean;
}

export interface HealthMetric {
  id: string;
  label: string;
  value: string;
  status: string;
}

export type PrescriptionStatus = "ACTIVE" | "RENEWAL_NEEDED" | "DISCONTINUED";

export interface PrescriptionItem {
  id: string;
  medicationName: string;
  dosage: string;
  instructions: string;
  prescribedBy: string;
  refillsRemaining: number;
  status: PrescriptionStatus;
}

export type LabReportStatus = "NEW" | "REVIEWED" | "PENDING";

export interface LabReportItem {
  id: string;
  title: string;
  date: string;
  orderedBy: string;
  status: LabReportStatus;
  category?: string;
}

export interface MedicalDocumentItem {
  id: string;
  title: string;
  date: string;
  provider: string;
  type: "NOTE" | "LAB" | "SUMMARY" | "DISCHARGE";
  fileSize?: string;
}

export type InvoiceStatus = "DRAFT" | "ISSUED" | "PARTIALLY_PAID" | "PAID" | "VOID";

export interface BillingInvoice {
  id: string;
  invoiceNumber: string;
  date: string;
  description: string;
  amount: string;
  status: InvoiceStatus;
  receiptUrl?: string;
}

export interface BillingSummary {
  outstandingBalance: string;
  dueInvoiceCount: number;
  paidThisYear: string;
  paidInvoiceCount: number;
  insuranceProvider: string;
  policyStatus: string;
  invoices: BillingInvoice[];
}

export interface PatientDetailedProfile {
  fullName: string;
  mrn: string;
  dateOfBirth: string;
  gender: string;
  bloodGroup: string;
  phone: string;
  email: string;
  address: string;
  emergencyContact: string;
  insuranceProvider: string;
  insurancePolicyNumber: string;
}

export interface FaqItem {
  id: string;
  question: string;
  answer: string;
}

export interface PatientDashboardData {
  profile: PatientProfile;
  detailedProfile: PatientDetailedProfile;
  unreadNotificationCount: number;
  stats: DashboardStat[];
  upcomingAppointment: UpcomingAppointment;
  allAppointments: AppointmentRecord[];
  recentAppointments: RecentAppointmentRow[];
  prescriptions: PrescriptionItem[];
  labReports: LabReportItem[];
  medicalDocuments: MedicalDocumentItem[];
  billing: BillingSummary;
  faqs: FaqItem[];
  notifications: NotificationItem[];
  healthMetrics: HealthMetric[];
}

