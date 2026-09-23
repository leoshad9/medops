import { Link } from "react-router-dom";
import { CalendarPlus, Clock, FileText, MapPin, Video } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { PATIENT_PATHS } from "../../lib/patientRoutes";
import { createCalendarDataUri, type CalendarEvent } from "../../lib/clinicTime";
import type { UpcomingAppointment } from "../../types/patient";

interface UpcomingAppointmentCardProps {
  appointment: UpcomingAppointment | null;
}

const STATUS_LABEL: Record<string, string> = {
  UPCOMING: "Confirmed",
  PENDING_CONFIRMATION: "Pending Confirmation",
  CANCELLED: "Cancelled",
  COMPLETED: "Completed",
};

const STATUS_ICON: Record<string, LucideIcon> = {
  UPCOMING: Clock,
  PENDING_CONFIRMATION: Clock,
  CANCELLED: FileText,
  COMPLETED: FileText,
};

export function UpcomingAppointmentCard({ appointment }: Readonly<UpcomingAppointmentCardProps>) {
  if (!appointment) {
    return (
      <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
        <h2 className="font-bold text-brand-ink text-base">Upcoming Appointment</h2>
        <p className="mt-4 text-sm text-brand-muted">No upcoming visit. Book a slot when you are ready.</p>
        <Link
          to={PATIENT_PATHS.book}
          className="mt-4 inline-flex rounded-lg bg-brand-primary px-4 py-2 text-xs font-semibold text-white shadow-2xs transition duration-150 hover:-translate-y-px hover:bg-brand-primary-dark hover:shadow-sm active:translate-y-0 focus-visible-ring"
        >
          Book Appointment
        </Link>
      </div>
    );
  }

  const Icon = STATUS_ICON[appointment.status] ?? Clock;
  const statusLabel = STATUS_LABEL[appointment.status] ?? appointment.status;
  const isTeleconsult = !!appointment.meetingLink;

  const calendarEvent: CalendarEvent = {
    id: `apt-${appointment.startsAt}`,
    startsAt: appointment.startsAt,
    endsAt: appointment.endsAt,
    summary: `Appointment with ${appointment.doctorName} (${appointment.specialty})`,
    location: appointment.location,
    description: appointment.reason ?? undefined,
  };

  return (
    <div className="relative overflow-hidden rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-10 -right-10 h-44 w-44 rounded-full bg-[radial-gradient(circle,var(--color-brand-primary-tint)_0%,transparent_70%)]"
      />

      <div className="relative flex items-center justify-between">
        <h2 className="font-bold text-brand-ink text-base">Upcoming Appointment</h2>
        <span
          className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
            appointment.status === "CANCELLED"
              ? "bg-brand-rust-tint text-brand-rust"
              : appointment.status === "COMPLETED"
                ? "bg-brand-success-tint text-brand-success"
                : "bg-brand-primary-tint text-brand-primary-dark"
          }`}
          aria-label={`Status: ${statusLabel}`}
        >
          <Icon className="h-3 w-3" aria-hidden="true" />
          {statusLabel}
        </span>
      </div>

      <div className="relative mt-4 flex flex-col sm:flex-row gap-5">
        <div className="flex w-20 shrink-0 flex-col items-center justify-center rounded-xl border border-brand-primary/20 bg-brand-primary-tint py-3 text-center text-brand-primary-dark shadow-2xs">
          <span className="font-brand-mono text-xl leading-none font-bold">{appointment.day}</span>
          <span className="mt-1 text-[11px] font-semibold tracking-wide">{appointment.month}</span>
          <span className="mt-0.5 text-[10px] opacity-80">{appointment.weekday}</span>
        </div>

        <div className="flex-1">
          <p className="text-lg font-bold text-brand-ink">{appointment.doctorName}</p>
          <p className="mt-0.5 text-sm font-semibold text-brand-primary-dark">{appointment.specialty}</p>
          <p className="mt-0.5 text-xs text-brand-muted">{appointment.department}</p>

          <div className="mt-3 flex flex-wrap gap-4 text-xs text-brand-muted">
            <span className="flex items-center gap-1.5">
              <Clock className="h-3.5 w-3.5 text-brand-primary" />
              <span className="font-brand-mono">{appointment.time}</span>
              <span className="font-semibold text-brand-ink">{appointment.timeRemaining}</span>
            </span>
            <span className="flex items-center gap-1.5">
              {isTeleconsult ? (
                <Video className="h-3.5 w-3.5 text-brand-primary" />
              ) : (
                <MapPin className="h-3.5 w-3.5 text-brand-primary" />
              )}
              {appointment.location}
            </span>
            <span className="flex items-center gap-1.5">
              <FileText className="h-3.5 w-3.5 text-brand-primary" />
              {appointment.visitType}
            </span>
          </div>

          {appointment.reason && (
            <p className="mt-3 text-sm text-brand-ink">Reason for visit: {appointment.reason}</p>
          )}

          <div className="mt-4 flex flex-wrap items-center gap-2.5">
            <Link
              to={PATIENT_PATHS.appointments}
              className="rounded-lg bg-brand-primary px-4 py-2 text-xs font-semibold text-white shadow-2xs transition duration-150 hover:-translate-y-px hover:bg-brand-primary-dark hover:shadow-sm active:translate-y-0 cursor-pointer focus-visible-ring"
            >
              View Details
            </Link>
            {isTeleconsult ? (
              <a
                href={appointment.meetingLink!}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-lg border border-brand-primary bg-white px-4 py-2 text-xs font-semibold text-brand-primary-dark transition duration-150 hover:-translate-y-px hover:bg-brand-primary-tint hover:shadow-sm active:translate-y-0 cursor-pointer focus-visible-ring"
              >
                Join Teleconsultation
              </a>
            ) : (
              <Link
                to={PATIENT_PATHS.appointments}
                className="rounded-lg border border-brand-primary bg-white px-4 py-2 text-xs font-semibold text-brand-primary-dark transition duration-150 hover:-translate-y-px hover:bg-brand-primary-tint hover:shadow-sm active:translate-y-0 cursor-pointer focus-visible-ring"
              >
                Reschedule / Cancel
              </Link>
            )}
            <a
              href={createCalendarDataUri(calendarEvent)}
              download="appointment.ics"
              className="rounded-lg border border-brand-line bg-white px-4 py-2 text-xs font-semibold text-brand-ink transition duration-150 hover:-translate-y-px hover:bg-brand-primary-tint hover:shadow-sm active:translate-y-0 cursor-pointer focus-visible-ring"
              aria-label="Add to calendar"
            >
              <span className="flex items-center gap-1.5">
                <CalendarPlus className="h-3.5 w-3.5" />
                Add to Calendar
              </span>
            </a>
          </div>

          {appointment.cancellationPolicy && (
            <p className="mt-3 text-xs text-brand-muted">
              {appointment.cancellationPolicy}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
