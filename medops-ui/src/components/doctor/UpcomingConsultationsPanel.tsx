import { ArrowUpRight, CalendarClock, ChevronRight, Stethoscope } from "lucide-react";
import { Link } from "react-router-dom";

import { formatClinicTime } from "../../lib/clinicTime";
import { DOCTOR_PATHS, doctorPatientChartPath } from "../../lib/doctorRoutes";
import type { AppointmentDto } from "../../services/appointmentService";

interface UpcomingConsultationsPanelProps {
  appointments: AppointmentDto[];
}

/**
 * Replaces the former mock "Live Patient Queue": shows the doctor's real
 * booked visits for today, straight from the dashboard aggregate. There is no
 * check-in domain yet, so there is deliberately no "Call In" action.
 */
export function UpcomingConsultationsPanel({
  appointments,
}: Readonly<UpcomingConsultationsPanelProps>) {
  return (
    <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
      <div className="flex items-center justify-between border-b border-brand-line pb-3">
        <div className="flex items-center gap-2">
          <CalendarClock className="h-4 w-4 text-brand-primary" />
          <h2 className="text-sm font-bold text-brand-ink">Upcoming Today</h2>
        </div>
        <span
          className="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-700"
          data-testid="upcoming-count"
        >
          {appointments.length} Booked
        </span>
      </div>

      {appointments.length === 0 ? (
        <div className="mt-4 text-center">
          <p className="mb-1 text-sm text-brand-muted">No more booked visits today.</p>
          <Link
            to={DOCTOR_PATHS.patients}
            className="inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
          >
            View patient roster
            <ArrowUpRight className="h-3.5 w-3.5" />
          </Link>
        </div>
      ) : (
        <div className="mt-4 space-y-3">
          {appointments.map((appointment) => (
            <div
              key={appointment.id}
              className="rounded-lg border border-brand-line/80 bg-slate-50/50 p-3.5 transition hover:border-brand-primary/40 hover:bg-white"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-semibold text-brand-ink">
                      {formatClinicTime(appointment.startsAt)}
                    </span>
                    <p className="truncate text-sm font-semibold text-brand-ink">
                      {appointment.patientName}
                    </p>
                  </div>
                  <p className="mt-0.5 font-mono text-xs text-brand-muted">{appointment.patientMrn}</p>
                  <p className="mt-1 flex items-center gap-1 text-xs text-slate-600">
                    <Stethoscope className="h-3 w-3" />
                    {appointment.reason ?? "Consultation"}
                  </p>
                </div>
                <Link
                  to={doctorPatientChartPath(appointment.patientId)}
                  className="flex shrink-0 items-center gap-1 rounded-lg bg-brand-primary px-2.5 py-1.5 text-xs font-semibold text-white shadow-xs transition hover:bg-brand-primary-dark"
                >
                  Chart
                  <ChevronRight className="h-3.5 w-3.5" />
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}