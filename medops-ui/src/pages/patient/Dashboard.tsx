import { useEffect, useState } from "react";

import { HealthSummaryPanel } from "../../components/patient/HealthSummaryPanel";
import { NotificationsPanel } from "../../components/patient/NotificationsPanel";
import { QuickActionsGrid } from "../../components/patient/QuickActionsGrid";
import { RecentAppointmentsTable } from "../../components/patient/RecentAppointmentsTable";
import { StatCardsRow } from "../../components/patient/StatCardsRow";
import { UpcomingAppointmentCard } from "../../components/patient/UpcomingAppointmentCard";
import { usePatientPortal } from "../../components/patient/usePatientPortal";
import { buildPatientDashboardLiveData } from "../../lib/patientDashboard";
import { listMyAppointments } from "../../services/appointmentService";
import { listPrescriptions, listReports } from "../../services/clinicalService";

export function PatientDashboard() {
  const { data } = usePatientPortal();
  const [live, setLive] = useState(buildPatientDashboardLiveData([], [], []));
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.all([listMyAppointments(), listPrescriptions(), listReports()])
      .then(([appointments, prescriptions, reports]) => {
        if (!cancelled) {
          setLive(buildPatientDashboardLiveData(appointments, prescriptions, reports));
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load dashboard data.");
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

  return (
    <div className="space-y-6">
      {error && (
        <p className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {error}
        </p>
      )}
      {loading && <p className="text-sm text-brand-muted">Loading your visits and records…</p>}

      <StatCardsRow stats={live.stats} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <UpcomingAppointmentCard appointment={live.upcomingAppointment} />
        </div>
        <QuickActionsGrid />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <RecentAppointmentsTable appointments={live.recentAppointments} />
        </div>
        <div className="space-y-6">
          <NotificationsPanel notifications={live.activity} />
          <HealthSummaryPanel metrics={data.healthMetrics} />
        </div>
      </div>
    </div>
  );
}
