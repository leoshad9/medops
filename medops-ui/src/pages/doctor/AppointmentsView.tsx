import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { doctorPatientChartPath } from "../../lib/doctorRoutes";
import { clinicDayBoundsIso, clinicTodayYmd, formatClinicDateTime, formatClinicTime } from "../../lib/clinicTime";
import {
  completeAppointment,
  listDoctorAppointments,
  type AppointmentDto,
} from "../../services/appointmentService";

type Tab = "today" | "upcoming";

function statusLabel(status: AppointmentDto["status"]): string {
  if (status === "BOOKED") {
    return "Confirmed";
  }
  if (status === "COMPLETED") {
    return "Completed";
  }
  return "Cancelled";
}

export function DoctorAppointmentsView() {
  const [tab, setTab] = useState<Tab>("today");
  const [items, setItems] = useState<AppointmentDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [completingId, setCompletingId] = useState<string | null>(null);

  const load = useCallback(async (which: Tab) => {
    if (which === "today") {
      const bounds = clinicDayBoundsIso(clinicTodayYmd());
      return listDoctorAppointments({ from: bounds.from, to: bounds.to });
    }
    return listDoctorAppointments({ status: "BOOKED" });
  }, []);

  useEffect(() => {
    let cancelled = false;
    load(tab)
      .then((rows) => {
        if (!cancelled) {
          setItems(rows.filter((row) => row.status !== "CANCELLED"));
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load appointments.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [load, tab]);

  const handleComplete = async (id: string) => {
    setCompletingId(id);
    try {
      await completeAppointment(id);
      setItems(await load(tab));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Unable to complete that visit.");
    } finally {
      setCompletingId(null);
    }
  };

  return (
    <section className="rounded-xl border border-brand-line bg-white p-6 shadow-xs">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-brand-line pb-4">
        <div>
          <h2 className="text-base font-bold text-brand-ink">
            {tab === "today" ? "Today's Appointments" : "Upcoming Appointments"}
          </h2>
          <p className="text-xs text-brand-muted mt-0.5">Patient, time, and status from the clinic schedule.</p>
        </div>
        <div className="flex gap-1 rounded-xl bg-brand-paper p-1 border border-brand-line">
          {(["today", "upcoming"] as const).map((key) => (
            <button
              key={key}
              type="button"
              onClick={() => setTab(key)}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold ${
                tab === key ? "bg-brand-primary text-white" : "text-brand-muted"
              }`}
            >
              {key === "today" ? "Today" : "Upcoming"}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <p className="mt-4 rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {error}
        </p>
      )}

      <div className="mt-4 overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="text-xs font-semibold uppercase tracking-wider text-brand-muted">
            <tr>
              <th className="pb-3 pr-4">Time</th>
              <th className="pb-3 pr-4">Patient</th>
              <th className="pb-3 pr-4">Reason</th>
              <th className="pb-3 pr-4">Status</th>
              <th className="pb-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-brand-line">
            {items.length === 0 ? (
              <tr>
                <td colSpan={5} className="py-10 text-center text-sm text-brand-muted">
                  No appointments in this view.
                </td>
              </tr>
            ) : (
              items.map((apt) => (
                <tr key={apt.id}>
                  <td className="py-3 pr-4 font-mono font-medium text-brand-ink">
                    {tab === "today" ? formatClinicTime(apt.startsAt) : formatClinicDateTime(apt.startsAt)}
                  </td>
                  <td className="py-3 pr-4">
                    <p className="font-semibold text-brand-ink">{apt.patientName}</p>
                    <p className="font-mono text-xs text-brand-muted">{apt.patientMrn}</p>
                  </td>
                  <td className="py-3 pr-4 text-xs text-brand-muted">{apt.reason ?? "—"}</td>
                  <td className="py-3 pr-4 text-xs font-semibold">{statusLabel(apt.status)}</td>
                  <td className="py-3 text-right space-x-3">
                    {apt.status === "BOOKED" && (
                      <button
                        type="button"
                        disabled={completingId === apt.id}
                        onClick={() => void handleComplete(apt.id)}
                        className="text-xs font-semibold text-brand-primary-dark hover:underline disabled:opacity-60"
                      >
                        {completingId === apt.id ? "Completing…" : "Complete"}
                      </button>
                    )}
                    <Link
                      to={doctorPatientChartPath(apt.patientId)}
                      className="text-xs font-semibold text-brand-primary-dark hover:underline"
                    >
                      Chart
                    </Link>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
