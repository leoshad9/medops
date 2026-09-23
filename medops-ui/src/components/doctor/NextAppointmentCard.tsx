import { Clock, Stethoscope, UserCheck, UserRound } from "lucide-react";
import { Link } from "react-router-dom";

import { recordAudit } from "../../lib/auditLog";
import { DOCTOR_PATHS, doctorPatientChartPath } from "../../lib/doctorRoutes";
import type { TodayAppointment } from "../../types/doctor";

interface NextAppointmentCardProps {
  appointment: TodayAppointment | null;
}

/**
 * A compact preview of the next patient the doctor needs to see. When the day is
 * clear, renders an empty state that points to the calendar. The check-in state
 * is derived from the persisted appointment status (the clinic domain has no
 * separate check-in flag yet, so a BOOKED visit is "Not arrived").
 */
export function NextAppointmentCard({ appointment }: Readonly<NextAppointmentCardProps>) {
  if (!appointment) {
    return (
      <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
        <div className="flex items-center gap-2">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-brand-primary-tint text-brand-primary-dark">
            <Clock className="h-4 w-4" />
          </span>
          <h2 className="text-base font-bold text-brand-ink">Next Appointment</h2>
        </div>
        <p className="mt-3 text-sm text-brand-muted">No appointments scheduled today.</p>
        <Link
          to={DOCTOR_PATHS.appointments}
          className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
        >
          View upcoming appointments
          <Stethoscope className="h-3.5 w-3.5" />
        </Link>
      </div>
    );
  }

  const chartPath = doctorPatientChartPath(appointment.patientId);
  const isNow = appointment.status === "CONFIRMED";

  return (
    <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
      <div className="flex items-center justify-between border-b border-brand-line pb-3">
        <div className="flex items-center gap-2">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-brand-primary-tint text-brand-primary-dark">
            <Clock className="h-4 w-4" />
          </span>
          <h2 className="text-base font-bold text-brand-ink">Next Appointment</h2>
        </div>
        <span
          className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${
            isNow
              ? "bg-emerald-50 text-emerald-700"
              : "bg-slate-100 text-slate-600"
          }`}
          aria-label={isNow ? "Current visit" : "Upcoming visit"}
        >
          <span
            className={`h-1.5 w-1.5 rounded-full ${isNow ? "bg-emerald-500" : "bg-slate-400"}`}
            aria-hidden="true"
          />
          {isNow ? "Now" : "Upcoming"}
        </span>
      </div>

      <div className="mt-4 space-y-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="font-mono text-xs font-semibold text-brand-primary-dark">{appointment.time}</p>
            <p className="mt-0.5 text-sm font-semibold text-brand-ink">{appointment.patientName}</p>
            <p className="font-mono text-xs text-brand-muted">{appointment.patientMrn}</p>
          </div>
          <span className="inline-flex items-center gap-1 rounded bg-brand-primary-tint px-2 py-0.5 text-[10px] font-semibold text-brand-primary-dark">
            <Stethoscope className="h-3 w-3" />
            {appointment.type}
          </span>
        </div>

        <p className="text-sm text-slate-700">
          <span className="font-semibold text-brand-ink">Chief complaint:</span>{" "}
          {appointment.reason}
        </p>

        <div className="flex items-center gap-2 text-xs">
          <UserCheck className="h-3.5 w-3.5 text-amber-600 shrink-0" />
          <span className="font-semibold text-brand-ink">Check-in:</span>
          <span className="inline-flex items-center gap-1 text-amber-700">
            <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
            Not arrived
          </span>
        </div>

        <div className="pt-2">
          <Link
            to={chartPath}
            onClick={() =>
              recordAudit("CONSULTATION_STARTED", {
                appointmentId: appointment.id,
                patientName: appointment.patientName,
              })
            }
            className="inline-flex items-center gap-1 rounded-lg bg-brand-primary px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-brand-primary-dark focus-visible-ring"
            aria-label={`Start consultation with ${appointment.patientName}`}
          >
            <UserRound className="h-3.5 w-3.5" />
            Start Consultation
          </Link>
        </div>
      </div>
    </div>
  );
}
