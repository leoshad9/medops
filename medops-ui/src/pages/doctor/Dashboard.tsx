import { useCallback, useEffect, useState } from "react";

import { DoctorStatCards } from "../../components/doctor/DoctorStatCards";
import { UpcomingConsultationsPanel } from "../../components/doctor/UpcomingConsultationsPanel";
import { PendingLabsPanel } from "../../components/doctor/PendingLabsPanel";
import { TodayScheduleTable } from "../../components/doctor/TodayScheduleTable";
import { calcAge, formatClinicTime } from "../../lib/clinicTime";
import {
  completeAppointment,
  type AppointmentDto,
} from "../../services/appointmentService";
import {
  getMyDashboard,
  type DoctorDashboard,
} from "../../services/doctorService";
import type {
  ClinicalAppointmentStatus,
  DoctorDashboardStat,
  TodayAppointment,
} from "../../types/doctor";

function toTodayRow(dto: AppointmentDto): TodayAppointment {
  const status: ClinicalAppointmentStatus =
    dto.status === "COMPLETED" ? "COMPLETED" : "CONFIRMED";
  return {
    id: dto.id,
    time: formatClinicTime(dto.startsAt),
    patientName: dto.patientName,
    patientMrn: dto.patientMrn,
    age: calcAge(dto.patientDateOfBirth),
    gender: dto.patientGender,
    reason: dto.reason ?? "—",
    type: "Clinic / In-Person",
    status,
  };
}

/**
 * KPI cards are derived from the same aggregate payload that feeds the
 * schedule, upcoming, and labs panels below, so the numbers always reconcile
 * with what the clinician sees when they open the detailed pages.
 */
function buildStats(dashboard: DoctorDashboard): DoctorDashboardStat[] {
  return [
    {
      id: "today-patients",
      label: "Today's Schedule",
      value: `${dashboard.todayScheduleCount} Patients`,
      sublabel: `${dashboard.completedTodayCount} completed · ${dashboard.awaitingTodayCount} awaiting`,
    },
    {
      id: "awaiting-consultation",
      label: "Awaiting Consultation",
      value: `${dashboard.awaitingTodayCount} Patients`,
      sublabel: "Booked visits remaining today",
    },
    {
      id: "lab-results",
      label: "Pending Lab Reviews",
      value: `${dashboard.pendingLabReportsCount} Reports`,
      sublabel: "New reports not yet reviewed",
      trend: dashboard.pendingLabReportsCount > 0 ? "Action needed" : "All clear",
      trendPositive: dashboard.pendingLabReportsCount === 0,
    },
  ];
}

export function DoctorDashboard() {
  const [dashboard, setDashboard] = useState<DoctorDashboard | null>(null);
  const [completingId, setCompletingId] = useState<string | null>(null);
  const [scheduleError, setScheduleError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadDashboard = useCallback(async () => {
    const data = await getMyDashboard();
    setDashboard(data);
    setScheduleError(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    loadDashboard().catch((err: unknown) => { // oxlint-disable-line react/set-state-in-effect
      if (!cancelled) {
        setScheduleError(err instanceof Error ? err.message : "Unable to load today's schedule.");
      }
    }).finally(() => {
      if (!cancelled) {
        setIsLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [loadDashboard]);

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

  return (
    <div className="space-y-6">
      {dashboard && <DoctorStatCards stats={buildStats(dashboard)} />}

      {scheduleError && (
        <div className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {scheduleError}
        </div>
      )}

      {isLoading && (
        <div className="rounded-xl border border-brand-line bg-white p-6 text-sm text-brand-muted">
          Loading today&apos;s schedule…
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="xl:col-span-2 space-y-6">
          {dashboard && (
            <TodayScheduleTable
              appointments={dashboard.todayAppointments.map(toTodayRow)}
              completingId={completingId}
              onComplete={handleComplete}
            />
          )}
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
