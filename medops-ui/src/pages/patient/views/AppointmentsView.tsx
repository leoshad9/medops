import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarPlus } from "lucide-react";

import { clinicTodayYmd, formatClinicTime } from "../../../lib/clinicTime";
import { PATIENT_PATHS } from "../../../lib/patientRoutes";
import {
  cancelAppointment as cancelAppointmentApi,
  listDoctorSlots,
  listMyAppointments,
  rescheduleAppointment as rescheduleAppointmentApi,
  toAppointmentRecord,
} from "../../../services/appointmentService";
import type { AppointmentRecord, AppointmentStatus } from "../../../types/patient";

type FilterType = "all" | "UPCOMING" | "COMPLETED" | "CANCELLED";

const STATUS_CONFIG: Record<AppointmentStatus, { label: string; className: string }> = {
  UPCOMING: { label: "Upcoming", className: "bg-brand-primary-tint text-brand-primary-dark" },
  COMPLETED: { label: "Completed", className: "bg-brand-success-tint text-brand-success" },
  CANCELLED: { label: "Cancelled", className: "bg-brand-rust-tint text-brand-rust" },
};

export function AppointmentsView() {
  const [appointments, setAppointments] = useState<AppointmentRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeFilter, setActiveFilter] = useState<FilterType>("all");
  const [selectedAppointment, setSelectedAppointment] = useState<AppointmentRecord | null>(null);
  const [actionNotice, setActionNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [rescheduleDate, setRescheduleDate] = useState(clinicTodayYmd());
  const [rescheduleSlot, setRescheduleSlot] = useState("");
  const [rescheduleSlots, setRescheduleSlots] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const reload = async () => {
    const items = await listMyAppointments();
    setAppointments(items.map(toAppointmentRecord));
  };

  useEffect(() => {
    let cancelled = false;
    setLoading(true); // oxlint-disable-line react/set-state-in-effect
    listMyAppointments()
      .then((items) => {
        if (!cancelled) {
          setAppointments(items.map(toAppointmentRecord));
          setActionError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setActionError(err instanceof Error ? err.message : "Unable to load appointments.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!selectedAppointment?.doctorId || selectedAppointment.status !== "UPCOMING") {
      setRescheduleSlots([]); // oxlint-disable-line react/set-state-in-effect
      return;
    }
    let cancelled = false;
    listDoctorSlots(selectedAppointment.doctorId, rescheduleDate)
      .then((slots) => {
        if (!cancelled) {
          setRescheduleSlots(slots);
          setRescheduleSlot("");
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRescheduleSlots([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [selectedAppointment, rescheduleDate]);

  const filteredAppointments = appointments.filter((apt) => {
    if (activeFilter === "all") return true;
    return apt.status === activeFilter;
  });

  const getCount = (filter: FilterType) => {
    if (filter === "all") return appointments.length;
    return appointments.filter((a) => a.status === filter).length;
  };

  const closeModal = () => setSelectedAppointment(null);

  const appointmentRows = () => {
    if (loading) {
      return (
        <tr>
          <td colSpan={5} className="py-12 text-center text-sm text-brand-muted">
            Loading appointments…
          </td>
        </tr>
      );
    }
    if (filteredAppointments.length === 0) {
      return (
        <tr>
          <td colSpan={5} className="py-12 text-center text-sm text-brand-muted">
            No appointments found for the selected filter.
          </td>
        </tr>
      );
    }
    return filteredAppointments.map((apt) => {
      const statusInfo = STATUS_CONFIG[apt.status];
      return (
        <tr key={apt.id} className="transition hover:bg-brand-paper/50">
          <td className="py-4 pr-4 font-brand-mono text-xs font-semibold text-brand-ink">
            {apt.dateTime}
          </td>
          <td className="py-4 pr-4 font-medium text-brand-ink">{apt.doctorName}</td>
          <td className="py-4 pr-4 text-brand-muted">
            <div>{apt.department}</div>
            {apt.location && <div className="text-xs text-brand-muted/80">{apt.location}</div>}
          </td>
          <td className="py-4 pr-4">
            <span
              className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusInfo.className}`}
            >
              {statusInfo.label}
            </span>
          </td>
          <td className="py-4 text-right">
            <button
              type="button"
              onClick={() => {
                setSelectedAppointment(apt);
                setRescheduleDate(clinicTodayYmd());
                setActionError(null);
              }}
              className="font-semibold text-brand-primary-dark hover:underline cursor-pointer text-xs"
            >
              {apt.status === "UPCOMING" ? "Reschedule / Cancel" : "View Summary"}
            </button>
          </td>
        </tr>
      );
    });
  };

  const requestReschedule = async () => {
    if (!selectedAppointment || !rescheduleSlot) return;
    setBusy(true);
    setActionError(null);
    try {
      await rescheduleAppointmentApi(selectedAppointment.id, rescheduleSlot);
      await reload();
      setActionNotice(`Your visit with ${selectedAppointment.doctorName} has been rescheduled.`);
      closeModal();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Unable to reschedule.");
    } finally {
      setBusy(false);
    }
  };

  const cancelAppointment = async () => {
    if (!selectedAppointment) return;
    setBusy(true);
    setActionError(null);
    try {
      await cancelAppointmentApi(selectedAppointment.id);
      await reload();
      setActionNotice(`Your appointment with ${selectedAppointment.doctorName} has been cancelled.`);
      closeModal();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Unable to cancel.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      {actionNotice && (
        <div className="rounded-xl border border-brand-success/30 bg-brand-success-tint px-4 py-3 text-sm text-brand-success">
          {actionNotice}
        </div>
      )}
      {actionError && (
        <div className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {actionError}
        </div>
      )}

      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between pb-4 border-b border-brand-line">
          <div>
            <h2 className="text-lg font-bold text-brand-ink">All Appointments</h2>
            <p className="text-xs text-brand-muted mt-0.5">
              Review your scheduled visits and past consults.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex flex-wrap gap-1.5 rounded-xl bg-brand-paper p-1 border border-brand-line">
              {(["all", "UPCOMING", "COMPLETED", "CANCELLED"] as const).map((filter) => {
                const label = filter === "all" ? "All" : filter.charAt(0) + filter.slice(1).toLowerCase();
                const isActive = activeFilter === filter;
                return (
                  <button
                    key={filter}
                    type="button"
                    onClick={() => setActiveFilter(filter)}
                    className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition cursor-pointer flex items-center gap-1.5 ${
                      isActive
                        ? "bg-brand-primary text-white shadow-2xs"
                        : "text-brand-muted hover:text-brand-ink"
                    }`}
                  >
                    <span>{label}</span>
                    <span
                      className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                        isActive ? "bg-white/20 text-white" : "bg-brand-line text-brand-muted"
                      }`}
                    >
                      {getCount(filter)}
                    </span>
                  </button>
                );
              })}
            </div>

            <Link
              to={PATIENT_PATHS.book}
              className="flex items-center gap-2 rounded-lg bg-brand-primary px-3.5 py-2 text-xs font-semibold text-white transition hover:bg-brand-primary-dark cursor-pointer shadow-2xs shrink-0"
            >
              <CalendarPlus className="h-4 w-4" />
              <span>Book Visit</span>
            </Link>
          </div>
        </div>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-brand-line text-xs font-semibold tracking-wider text-brand-muted uppercase">
                <th className="pb-3 pr-4">Date & Time</th>
                <th className="pb-3 pr-4">Doctor</th>
                <th className="pb-3 pr-4">Department / Clinic</th>
                <th className="pb-3 pr-4">Status</th>
                <th className="pb-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-brand-line">
              {appointmentRows()}
            </tbody>
          </table>
        </div>
      </section>

      {selectedAppointment && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-brand-ink/40 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-2xl border border-brand-line bg-white p-6 shadow-xl">
            <h3 className="text-lg font-bold text-brand-ink">Appointment Details</h3>
            <div className="mt-4 space-y-3 text-sm">
              <div className="rounded-xl bg-brand-paper p-3.5 space-y-1.5 border border-brand-line">
                <p className="font-semibold text-brand-ink text-base">{selectedAppointment.doctorName}</p>
                <p className="text-xs text-brand-primary-dark font-medium">{selectedAppointment.department}</p>
                <p className="font-brand-mono text-xs text-brand-muted">{selectedAppointment.dateTime}</p>
                {selectedAppointment.location && (
                  <p className="text-xs text-brand-muted">{selectedAppointment.location}</p>
                )}
                {selectedAppointment.reason && (
                  <p className="text-xs text-brand-muted italic mt-1">&quot;{selectedAppointment.reason}&quot;</p>
                )}
              </div>

              {selectedAppointment.status === "UPCOMING" && (
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <label htmlFor="reschedule-date" className="text-xs font-semibold text-brand-ink">
                      New date
                    </label>
                    <input
                      id="reschedule-date"
                      type="date"
                      min={clinicTodayYmd()}
                      value={rescheduleDate}
                      onChange={(e) => setRescheduleDate(e.target.value)}
                      className="w-full rounded-lg border border-brand-line px-3 py-2 text-xs"
                    />
                  </div>
                  <div className="space-y-1">
                    <label htmlFor="reschedule-slot" className="text-xs font-semibold text-brand-ink">
                      New time
                    </label>
                    <select
                      id="reschedule-slot"
                      value={rescheduleSlot}
                      onChange={(e) => setRescheduleSlot(e.target.value)}
                      className="w-full rounded-lg border border-brand-line px-3 py-2 text-xs"
                    >
                      <option value="">Select slot</option>
                      {rescheduleSlots.map((slot) => (
                        <option key={slot} value={slot}>
                          {formatClinicTime(slot)}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
              )}

              <div className="flex items-center justify-between text-xs text-brand-muted pt-2">
                <span>Status:</span>
                <span
                  className={`rounded-full px-2.5 py-0.5 font-semibold ${
                    STATUS_CONFIG[selectedAppointment.status].className
                  }`}
                >
                  {STATUS_CONFIG[selectedAppointment.status].label}
                </span>
              </div>
            </div>

            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <button
                type="button"
                onClick={closeModal}
                className="rounded-lg border border-brand-line px-4 py-2 text-xs font-semibold text-brand-ink hover:bg-brand-paper cursor-pointer"
              >
                Close
              </button>
              {selectedAppointment.status === "UPCOMING" && (
                <>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={cancelAppointment}
                    className="rounded-lg border border-brand-rust px-4 py-2 text-xs font-semibold text-brand-rust hover:bg-brand-rust-tint cursor-pointer disabled:opacity-60"
                  >
                    Cancel Appointment
                  </button>
                  <button
                    type="button"
                    disabled={busy || !rescheduleSlot}
                    onClick={requestReschedule}
                    className="rounded-lg bg-brand-primary px-4 py-2 text-xs font-semibold text-white hover:bg-brand-primary-dark cursor-pointer disabled:opacity-60"
                  >
                    Reschedule
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
