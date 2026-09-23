import { ArrowUpRight, CheckCircle2, Clock, Stethoscope, XCircle } from "lucide-react";
import { Link } from "react-router-dom";

import { DOCTOR_PATHS, doctorPatientChartPath } from "../../lib/doctorRoutes";
import type {
  ClinicalAppointmentStatus,
  ScheduleFilter,
  TodayAppointment,
} from "../../types/doctor";

interface TodayScheduleTableProps {
  appointments: TodayAppointment[];
  filter: ScheduleFilter;
  onFilterChange: (filter: ScheduleFilter) => void;
  completingId?: string | null;
  onComplete?: (appointmentId: string) => void;
}

const FILTERS: { value: ScheduleFilter; label: string }[] = [
  { value: "today", label: "Today" },
  { value: "upcoming", label: "Upcoming" },
  { value: "completed", label: "Completed" },
  { value: "cancelled", label: "Cancelled" },
];

function renderStatusBadge(status: ClinicalAppointmentStatus) {
  switch (status) {
    case "CANCELLED":
      return (
        <span
          className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600"
          data-testid="status-badge"
        >
          <XCircle className="h-3 w-3" />
          Cancelled
        </span>
      );
    case "COMPLETED":
      return (
        <span
          className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600"
          data-testid="status-badge"
        >
          <CheckCircle2 className="h-3 w-3" />
          Completed
        </span>
      );
    case "CONFIRMED":
    default:
      return (
        <span
          className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700"
          data-testid="status-badge"
        >
          <Clock className="h-3 w-3" />
          Scheduled
        </span>
      );
  }
}

function emptyStateMessage(filter: ScheduleFilter): string {
  switch (filter) {
    case "cancelled":
      return "No cancelled appointments for today.";
    case "completed":
      return "No completed visits yet today.";
    case "upcoming":
      return "No upcoming appointments today.";
    default:
      return "No appointments scheduled for today.";
  }
}

function emptyStateCta(filter: ScheduleFilter): { label: string; path: string } | null {
  if (filter === "today") {
    return { label: "View upcoming appointments", path: DOCTOR_PATHS.appointments };
  }
  if (filter === "upcoming") {
    return { label: "View all appointments", path: DOCTOR_PATHS.appointments };
  }
  return null;
}

export function TodayScheduleTable({
  appointments,
  filter,
  onFilterChange,
  completingId,
  onComplete,
}: Readonly<TodayScheduleTableProps>) {
  return (
    <div className="rounded-xl border border-brand-line bg-white shadow-xs">
      <div className="flex items-center justify-between border-b border-brand-line px-6 py-4">
        <div>
          <h2 className="text-base font-bold text-brand-ink">Today&apos;s Patient Schedule</h2>
          <p className="text-xs text-brand-muted">Consultation &amp; examination roster for today</p>
        </div>
        <Link
          to={DOCTOR_PATHS.appointments}
          className="inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
        >
          View Full Calendar
          <ArrowUpRight className="h-3.5 w-3.5" />
        </Link>
      </div>

      <div className="border-b border-brand-line px-6 py-3">
        <div className="flex gap-1 rounded-xl bg-brand-paper p-1 border border-brand-line">
          {FILTERS.map(({ value, label }) => (
            <button
              key={value}
              type="button"
              onClick={() => onFilterChange(value)}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                filter === value
                  ? "bg-brand-primary text-white"
                  : "text-brand-muted hover:text-brand-ink"
              } focus-visible-ring`}
              data-testid={`filter-${value}`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {appointments.length === 0 ? (
        <div className="px-6 py-8 text-center">
          <p className="text-sm text-brand-muted">{emptyStateMessage(filter)}</p>
          {(() => {
            const cta = emptyStateCta(filter);
            return cta ? (
              <Link
                to={cta.path}
                className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
              >
                {cta.label}
                <ArrowUpRight className="h-3.5 w-3.5" />
              </Link>
            ) : null;
          })()}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm font-brand-sans">
            <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wider text-brand-muted">
              <tr>
                <th className="px-6 py-3">Time</th>
                <th className="px-6 py-3">Patient &amp; MRN</th>
                <th className="px-6 py-3">Visit Type</th>
                <th className="px-6 py-3">Reason</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3">Location</th>
                <th className="px-6 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-brand-line/60">
              {appointments.map((apt) => (
                <tr
                  key={apt.id}
                  className="transition hover:bg-slate-50/80"
                  data-testid="schedule-row"
                >
                  <td className="px-6 py-4 font-mono font-medium text-brand-ink">{apt.time}</td>
                  <td className="px-6 py-4">
                    <p className="font-semibold text-brand-ink">{apt.patientName}</p>
                    <p className="font-mono text-xs text-brand-muted">{apt.patientMrn}</p>
                  </td>
                  <td className="px-6 py-4 text-xs text-brand-muted" data-testid="visit-type">
                    {apt.type}
                  </td>
                  <td className="px-6 py-4 text-xs text-slate-700 max-w-xs truncate">{apt.reason}</td>
                  <td className="px-6 py-4">{renderStatusBadge(apt.status)}</td>
                  <td className="px-6 py-4 text-xs text-brand-muted" data-testid="location">
                    {apt.location}
                  </td>
                  <td className="px-6 py-4 text-right">
                    {apt.status === "CONFIRMED" && (
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          disabled={completingId === apt.id}
                          onClick={() => onComplete?.(apt.id)}
                          data-testid="complete-visit"
                          className="rounded-lg border border-brand-line bg-white px-3 py-1.5 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary hover:text-white disabled:opacity-60"
                        >
                          {completingId === apt.id ? "Completing…" : "Complete visit"}
                        </button>
                        <Link
                          to={doctorPatientChartPath(apt.patientId)}
                          title="Start consultation"
                          className="inline-flex items-center gap-1 rounded-lg bg-brand-primary px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-brand-primary-dark focus-visible-ring"
                          data-testid="start-consultation"
                        >
                          <Stethoscope className="h-3.5 w-3.5" />
                          Start
                        </Link>
                      </div>
                    )}
                    {apt.status === "COMPLETED" && (
                      <Link
                        to={doctorPatientChartPath(apt.patientId)}
                        title="Open patient chart"
                        className="inline-flex items-center gap-1 rounded-lg border border-brand-line bg-white px-3 py-1.5 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary hover:text-white focus-visible-ring"
                      >
                        Chart
                      </Link>
                    )}
                    {apt.status === "CANCELLED" && (
                      <span className="text-xs text-brand-muted">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
