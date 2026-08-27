import { HealthSummaryPanel } from "../../components/patient/HealthSummaryPanel";
import { NotificationsPanel } from "../../components/patient/NotificationsPanel";
import { QuickActionsGrid } from "../../components/patient/QuickActionsGrid";
import { RecentAppointmentsTable } from "../../components/patient/RecentAppointmentsTable";
import { StatCardsRow } from "../../components/patient/StatCardsRow";
import { UpcomingAppointmentCard } from "../../components/patient/UpcomingAppointmentCard";
import { usePatientPortal } from "../../components/patient/usePatientPortal";

export function PatientDashboard() {
  const { data } = usePatientPortal();

  return (
    <div className="space-y-6">
      <StatCardsRow stats={data.stats} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <UpcomingAppointmentCard appointment={data.upcomingAppointment} />
        </div>
        <QuickActionsGrid />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <RecentAppointmentsTable appointments={data.recentAppointments} />
        </div>
        <div className="space-y-6">
          <NotificationsPanel notifications={data.notifications} />
          <HealthSummaryPanel metrics={data.healthMetrics} />
        </div>
      </div>
    </div>
  );
}
