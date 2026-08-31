import type { AppointmentDto } from "../services/appointmentService";
import type { ClinicalReportDto, PrescriptionDto } from "../services/clinicalService";
import {
  formatClinicDateTime,
  formatClinicTime,
  nextAppointmentStat,
  upcomingCardParts,
} from "./clinicTime";
import type {
  DashboardStat,
  NotificationItem,
  RecentAppointmentRow,
  UpcomingAppointment,
} from "../types/patient";
import { toAppointmentRecord } from "../services/appointmentService";

export interface PatientDashboardLiveData {
  stats: DashboardStat[];
  upcomingAppointment: UpcomingAppointment | null;
  recentAppointments: RecentAppointmentRow[];
  activity: NotificationItem[];
}

export function buildPatientDashboardLiveData(
  appointments: AppointmentDto[],
  prescriptions: PrescriptionDto[],
  reports: ClinicalReportDto[],
): PatientDashboardLiveData {
  const now = Date.now();
  const bookedFuture = appointments
    .filter((item) => item.status === "BOOKED" && new Date(item.startsAt).getTime() > now)
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const next = bookedFuture[0] ?? null;
  const nextStat = next ? nextAppointmentStat(next.startsAt) : { value: "None", sublabel: "No visit booked" };
  const activeRx = prescriptions.filter((item) => item.status === "ACTIVE").length;
  const newReports = reports.filter((item) => item.status === "NEW").length;

  return {
    stats: [
      {
        id: "next-appointment",
        label: "Next Appointment",
        value: nextStat.value,
        sublabel: nextStat.sublabel,
        linkLabel: "View details",
      },
      {
        id: "prescriptions",
        label: "Prescriptions",
        value: String(activeRx),
        sublabel: "Active",
        linkLabel: "View all",
      },
      {
        id: "lab-reports",
        label: "Lab Reports",
        value: String(newReports),
        sublabel: newReports === 1 ? "New result" : "New results",
        linkLabel: "View reports",
      },
      {
        id: "medical-records",
        label: "Medical Records",
        value: "—",
        sublabel: "Coming soon",
        linkLabel: "View all",
      },
    ],
    upcomingAppointment: next ? toUpcomingCard(next) : null,
    recentAppointments: appointments.slice(0, 5).map(toAppointmentRecord),
    activity: buildActivity(next, prescriptions, reports),
  };
}

function toUpcomingCard(dto: AppointmentDto): UpcomingAppointment {
  const parts = upcomingCardParts(dto.startsAt);
  return {
    day: parts.day,
    month: parts.month,
    weekday: parts.weekday,
    doctorName: dto.doctorName,
    specialty: dto.specialty,
    time: formatClinicTime(dto.startsAt),
    location: dto.location ?? "Clinic",
    visitType: dto.reason ?? "Consultation",
  };
}

function buildActivity(
  next: AppointmentDto | null,
  prescriptions: PrescriptionDto[],
  reports: ClinicalReportDto[],
): NotificationItem[] {
  const items: NotificationItem[] = [];
  if (next) {
    items.push({
      id: `apt-${next.id}`,
      title: "Upcoming visit",
      description: `${next.doctorName} · ${formatClinicDateTime(next.startsAt)}`,
      timeAgo: nextAppointmentStat(next.startsAt).value,
      unread: true,
    });
  }
  for (const report of reports.filter((item) => item.status === "NEW").slice(0, 3)) {
    items.push({
      id: `rep-${report.id}`,
      title: "New report",
      description: `${report.title} · ${report.doctorName}`,
      timeAgo: formatClinicDateTime(report.createdAt),
      unread: true,
    });
  }
  for (const rx of prescriptions.filter((item) => item.status === "ACTIVE").slice(0, 2)) {
    items.push({
      id: `rx-${rx.id}`,
      title: "Prescription on file",
      description: `${rx.medicationName} · ${rx.doctorName}`,
      timeAgo: formatClinicDateTime(rx.createdAt),
    });
  }
  return items.slice(0, 6);
}
