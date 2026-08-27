import { Link } from "react-router-dom";
import { Clock, FileText, MapPin } from "lucide-react";

import { PATIENT_PATHS } from "../../lib/patientRoutes";
import type { UpcomingAppointment } from "../../types/patient";

interface UpcomingAppointmentCardProps {
  appointment: UpcomingAppointment;
}

export function UpcomingAppointmentCard({ appointment }: Readonly<UpcomingAppointmentCardProps>) {
  return (
    <div className="relative overflow-hidden rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-10 -right-10 h-44 w-44 rounded-full bg-[radial-gradient(circle,var(--color-brand-primary-tint)_0%,transparent_70%)]"
      />

      <div className="relative flex items-center justify-between">
        <h2 className="font-bold text-brand-ink text-base">Upcoming Appointment</h2>
        <Link to={PATIENT_PATHS.appointments} className="text-xs font-semibold text-brand-primary-dark hover:underline cursor-pointer">
          View All
        </Link>
      </div>

      <div className="relative mt-4 flex flex-col sm:flex-row gap-5">
        <div className="flex w-24 shrink-0 flex-col items-center justify-center rounded-xl bg-brand-primary py-3.5 text-center text-white shadow-2xs">
          <span className="font-brand-mono text-2xl leading-none font-semibold">{appointment.day}</span>
          <span className="mt-1 text-[11px] font-semibold tracking-wide">{appointment.month}</span>
          <span className="mt-0.5 text-[10px] opacity-80">{appointment.weekday}</span>
        </div>

        <div className="flex-1">
          <p className="text-lg font-bold text-brand-ink">{appointment.doctorName}</p>
          <p className="mt-0.5 mb-3 text-sm font-semibold text-brand-primary-dark">{appointment.specialty}</p>

          <div className="flex flex-wrap gap-4 text-xs text-brand-muted">
            <span className="flex items-center gap-1.5">
              <Clock className="h-3.5 w-3.5 text-brand-primary" />
              <span className="font-brand-mono">{appointment.time}</span>
            </span>
            <span className="flex items-center gap-1.5">
              <MapPin className="h-3.5 w-3.5 text-brand-primary" />
              {appointment.location}
            </span>
            <span className="flex items-center gap-1.5">
              <FileText className="h-3.5 w-3.5 text-brand-primary" />
              {appointment.visitType}
            </span>
          </div>

          <div className="mt-4 flex flex-wrap gap-2.5">
            <Link
              to={PATIENT_PATHS.appointments}
              className="rounded-lg bg-brand-primary px-4 py-2 text-xs font-semibold text-white transition hover:bg-brand-primary-dark cursor-pointer shadow-2xs"
            >
              View Details
            </Link>
            <Link
              to={PATIENT_PATHS.appointments}
              className="rounded-lg border border-brand-primary bg-white px-4 py-2 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary-tint cursor-pointer"
            >
              Reschedule / Cancel
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
