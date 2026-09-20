import { ArrowUpRight, CheckCircle2, Clock } from "lucide-react";
import { Link } from "react-router-dom";

import { formatDemographics } from "../../lib/clinicTime";
import { DOCTOR_PATHS } from "../../lib/doctorRoutes";
import type { ClinicalAppointmentStatus, TodayAppointment } from "../../types/doctor";

interface TodayScheduleTableProps {
  appointments: TodayAppointment[];
  completingId?: string | null;
  onComplete?: (appointmentId: string) => void;
}

function renderStatusBadge(status: ClinicalAppointmentStatus) {
  switch (status) {
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

export function TodayScheduleTable({
  appointments,
  completingId,
  onComplete,
}: Readonly<TodayScheduleTableProps>) {
  return (
    <div className="rounded-xl border border-brand-line bg-white shadow-xs">
      <div className="flex items-center justify-between border-b border-brand-line px-6 py-4">
        <div>
          <h2 className="text-base font-bold text-brand-ink">Today&apos;s Patient Schedule</h2>
          <p className="text-xs text-brand-muted">Consultation & examination roster for today</p>
        </div>
        <Link
          to={DOCTOR_PATHS.appointments}
          className="inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
        >
          View Full Calendar
          <ArrowUpRight className="h-3.5 w-3.5" />
        </Link>
      </div>

      {appointments.length === 0 ? (
        <p className="px-6 py-8 text-sm text-brand-muted">No appointments scheduled for today.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm font-brand-sans">
            <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wider text-brand-muted">
              <tr>
                <th className="px-6 py-3">Time</th>
                <th className="px-6 py-3">Patient & MRN</th>
                <th className="px-6 py-3">Demographics</th>
                <th className="px-6 py-3">Reason for Visit</th>
                <th className="px-6 py-3">Status</th>
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
                  <td className="px-6 py-4 text-xs text-brand-muted" data-testid="demographics">
                    {formatDemographics(apt.age, apt.gender)}
                  </td>
                  <td className="px-6 py-4 text-xs text-slate-700 max-w-xs truncate">{apt.reason}</td>
                  <td className="px-6 py-4">{renderStatusBadge(apt.status)}</td>
                  <td className="px-6 py-4 text-right">
                    {apt.status === "CONFIRMED" && onComplete ? (
                      <button
                        type="button"
                        disabled={completingId === apt.id}
                        onClick={() => onComplete(apt.id)}
                        data-testid="complete-visit"
                        className="rounded-lg border border-brand-line bg-white px-3 py-1.5 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary hover:text-white disabled:opacity-60"
                      >
                        {completingId === apt.id ? "Completing…" : "Complete visit"}
                      </button>
                    ) : (
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
