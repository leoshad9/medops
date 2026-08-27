import { Link } from "react-router-dom";

import { PATIENT_PATHS } from "../../lib/patientRoutes";
import type { AppointmentStatus, RecentAppointmentRow } from "../../types/patient";

const STATUS_STYLES: Record<AppointmentStatus, string> = {
  COMPLETED: "bg-brand-success-tint text-brand-success",
  CANCELLED: "bg-brand-rust-tint text-brand-rust",
  UPCOMING: "bg-brand-primary-tint text-brand-primary-dark",
};

const STATUS_LABELS: Record<AppointmentStatus, string> = {
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  UPCOMING: "Upcoming",
};

interface RecentAppointmentsTableProps {
  appointments: RecentAppointmentRow[];
}

export function RecentAppointmentsTable({ appointments }: Readonly<RecentAppointmentsTableProps>) {
  return (
    <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
      <div className="flex items-center justify-between">
        <h2 className="font-bold text-brand-ink text-base">Recent Appointments</h2>
        <Link to={PATIENT_PATHS.appointments} className="text-xs font-semibold text-brand-primary-dark hover:underline cursor-pointer">
          View All
        </Link>
      </div>

      <div className="mt-4 overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-brand-line text-xs tracking-wider text-brand-muted uppercase font-semibold">
              <th className="pb-2.5">Date &amp; Time</th>
              <th className="pb-2.5">Doctor</th>
              <th className="pb-2.5">Department</th>
              <th className="pb-2.5">Status</th>
              <th className="pb-2.5 text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-brand-line/60">
            {appointments.map((appointment) => (
              <tr key={appointment.id} className="transition hover:bg-brand-paper/50">
                <td className="py-3 font-brand-mono text-xs font-medium text-brand-ink">{appointment.dateTime}</td>
                <td className="py-3 font-medium text-brand-ink">{appointment.doctorName}</td>
                <td className="py-3 text-brand-muted">{appointment.department}</td>
                <td className="py-3">
                  <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLES[appointment.status]}`}>
                    {STATUS_LABELS[appointment.status]}
                  </span>
                </td>
                <td className="py-3 text-right">
                  <Link
                    to={PATIENT_PATHS.appointments}
                    className="font-semibold text-brand-primary-dark hover:underline cursor-pointer text-xs"
                  >
                    {appointment.status === "CANCELLED" ? "View Details" : "View Summary"}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
