import { useCallback, useEffect, useState } from "react";
import { useOutletContext } from "react-router-dom";

import { DoctorStatCards } from "../../components/doctor/DoctorStatCards";
import { NextAppointmentCard } from "../../components/doctor/NextAppointmentCard";
import { PendingLabsPanel } from "../../components/doctor/PendingLabsPanel";
import { PrescriptionTasks } from "../../components/doctor/PrescriptionTasks";
import { TodayScheduleTable } from "../../components/doctor/TodayScheduleTable";
import { UrgentAlertsPanel } from "../../components/doctor/UrgentAlertsPanel";
import { UpcomingConsultationsPanel } from "../../components/doctor/UpcomingConsultationsPanel";
import { QuickActionsBar } from "../../components/doctor/QuickActionsBar";
import { listPrescriptions, type PrescriptionDto } from "../../services/clinicalService";
import {
  buildStats,
  deriveNextAppointment,
  derivePrescriptionTasks,
  deriveUrgentAlerts,
  filterAppointments,
  toTodayRow,
} from "../../lib/doctorDashboard";
import { completeAppointment } from "../../services/appointmentService";
import { getMyDashboard, type DoctorDashboard } from "../../services/doctorService";
import { recordAudit } from "../../lib/auditLog";
import type { DoctorLayoutContext } from "../../components/doctor/DoctorLayout";
import type { ScheduleFilter } from "../../types/doctor";

export function DoctorDashboard() {
  const { notifications, markNotificationRead, setPendingClinicalWorkItems } =
    useOutletContext<DoctorLayoutContext>();
  const [dashboard, setDashboard] = useState<DoctorDashboard | null>(null);
  const [prescriptions, setPrescriptions] = useState<PrescriptionDto[]>([]);
  const [filter, setFilter] = useState<ScheduleFilter>("today");
  const [completingId, setCompletingId] = useState<string | null>(null);
  const [prescriptionsError, setPrescriptionsError] = useState<string | null>(null);
  const [scheduleError, setScheduleError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [acknowledgedAlertIds, setAcknowledgedAlertIds] = useState<Set<string>>(new Set());

  const loadDashboard = useCallback(async () => {
    const data = await getMyDashboard();
    setDashboard(data);
    setScheduleError(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      getMyDashboard()
        .then((data) => {
          if (!cancelled) {
            setDashboard(data);
            setScheduleError(null);
          }
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            setScheduleError(
              err instanceof Error ? err.message : "Unable to load today's schedule.",
            );
          }
        }),
      listPrescriptions()
        .then((items) => {
          if (!cancelled) {
            setPrescriptions(items);
            setPrescriptionsError(null);
          }
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            setPrescriptionsError(
              err instanceof Error ? err.message : "Unable to load prescriptions.",
            );
          }
        }),
    ]).finally(() => {
      if (!cancelled) {
        setIsLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // Surface the day's real pending work to the shared duty-status confirmation
  // so the doctor is warned before going Off Duty.
  useEffect(() => {
    if (dashboard) {
      const items: string[] = [];
      if (dashboard.pendingLabReportsCount > 0) {
        items.push(`${dashboard.pendingLabReportsCount} lab report${dashboard.pendingLabReportsCount === 1 ? "" : "s"} need review`);
      }
      if (dashboard.awaitingTodayCount > 0) {
        items.push(
          `${dashboard.awaitingTodayCount} patient${dashboard.awaitingTodayCount === 1 ? "" : "s"} awaiting consultation`,
        );
      }
      setPendingClinicalWorkItems(items);
    }
  }, [dashboard, setPendingClinicalWorkItems]);

  const handleComplete = async (appointmentId: string) => {
    setCompletingId(appointmentId);
    try {
      await completeAppointment(appointmentId);
      await loadDashboard();
    } catch (err: unknown) {
      setScheduleError(err instanceof Error ? err.message : "Unable to complete that visit.");
    } finally {
      setCompletingId(null);
    }
  };

  const handleAcknowledge = (alert: {
    id: string;
    category: string;
    referenceId?: string;
    patientName?: string;
  }) => {
    setAcknowledgedAlertIds((prev) => new Set([...prev, alert.id]));
    if (alert.category === "LAB") {
      recordAudit("LAB_ACKNOWLEDGED", {
        reportId: alert.referenceId,
        patientName: alert.patientName,
      });
    } else if (alert.category === "NOTIFICATION" && alert.referenceId) {
      recordAudit("NOTIFICATION_ACKNOWLEDGED", { notificationId: alert.referenceId });
      void markNotificationRead(alert.referenceId);
    }
  };

  const rows = dashboard?.todayAppointments.map(toTodayRow) ?? [];
  const prescriptionTasks = derivePrescriptionTasks(prescriptions);

  return (
    <div className="space-y-6">
      <QuickActionsBar />

      {isLoading && (
        <div className="rounded-xl border border-brand-line bg-white p-6 text-sm text-brand-muted">
          Loading your clinical workspace…
        </div>
      )}

      {scheduleError && (
        <div className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {scheduleError}
        </div>
      )}

      {prescriptionsError && (
        <div className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {prescriptionsError}
        </div>
      )}

      {dashboard && (
        <DoctorStatCards stats={buildStats(dashboard, prescriptionTasks.length)} />
      )}

      {dashboard && (
        <UrgentAlertsPanel
          alerts={deriveUrgentAlerts(dashboard.pendingLabReports, notifications).filter(
            (alert) => !acknowledgedAlertIds.has(alert.id),
          )}
          onAcknowledge={(alert) =>
            handleAcknowledge({
              id: alert.id,
              category: alert.category,
              referenceId: alert.referenceId,
              patientName: alert.patientName,
            })
          }
        />
      )}

      {dashboard && <NextAppointmentCard appointment={deriveNextAppointment(rows)} />}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="xl:col-span-2 space-y-6">
          {dashboard && (
            <TodayScheduleTable
              appointments={filterAppointments(rows, filter)}
              filter={filter}
              onFilterChange={setFilter}
              completingId={completingId}
              onComplete={handleComplete}
            />
          )}
          <PrescriptionTasks tasks={prescriptionTasks} />
        </div>
        <div className="space-y-6">
          {dashboard && (
            <UpcomingConsultationsPanel appointments={dashboard.upcomingAppointments} />
          )}
          {dashboard && <PendingLabsPanel labs={dashboard.pendingLabReports} />}
        </div>
      </div>
    </div>
  );
}
