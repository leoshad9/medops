import { useCallback, useEffect, useState } from "react";

import { DoctorStatCards } from "../../components/doctor/DoctorStatCards";
import { PatientQueuePanel } from "../../components/doctor/PatientQueuePanel";
import { PendingLabsPanel } from "../../components/doctor/PendingLabsPanel";
import { TodayScheduleTable } from "../../components/doctor/TodayScheduleTable";
import { clinicDayBoundsIso, clinicTodayYmd, formatClinicTime } from "../../lib/clinicTime";
import {
  completeAppointment,
  listDoctorAppointments,
  type AppointmentDto,
} from "../../services/appointmentService";
import type { ClinicalAppointmentStatus, TodayAppointment } from "../../types/doctor";
import { mockDoctorDashboard } from "./mockDoctorData";

function toTodayRow(dto: AppointmentDto): TodayAppointment {
  const status: ClinicalAppointmentStatus = dto.status === "COMPLETED" ? "COMPLETED" : "CONFIRMED";
  return {
    id: dto.id,
    time: formatClinicTime(dto.startsAt),
    patientName: dto.patientName,
    patientMrn: dto.patientMrn,
    age: 0,
    gender: "—",
    reason: dto.reason ?? "—",
    type: "Clinic / In-Person",
    status,
  };
}

export function DoctorDashboard() {
  const data = mockDoctorDashboard;
  const [todayAppointments, setTodayAppointments] = useState<TodayAppointment[]>([]);
  const [scheduleFromApi, setScheduleFromApi] = useState(false);
  const [completingId, setCompletingId] = useState<string | null>(null);
  const [scheduleError, setScheduleError] = useState<string | null>(null);

  const loadToday = useCallback(async () => {
    const bounds = clinicDayBoundsIso(clinicTodayYmd());
    const items = await listDoctorAppointments({ from: bounds.from, to: bounds.to });
    setTodayAppointments(items.filter((item) => item.status !== "CANCELLED").map(toTodayRow));
    setScheduleFromApi(true);
    setScheduleError(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    loadToday().catch((err: unknown) => { // oxlint-disable-line react/set-state-in-effect
      if (!cancelled) {
        setScheduleError(err instanceof Error ? err.message : "Unable to load today's schedule.");
      }
    });
    return () => {
      cancelled = true;
    };
  }, [loadToday]);

  const handleComplete = async (appointmentId: string) => {
    setCompletingId(appointmentId);
    try {
      await completeAppointment(appointmentId);
      await loadToday();
    } catch (err: unknown) {
      setScheduleError(err instanceof Error ? err.message : "Unable to complete that visit.");
    } finally {
      setCompletingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <DoctorStatCards stats={data.stats} />

        {scheduleError && (
          <div className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
            {scheduleError}
          </div>
        )}

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
          <div className="xl:col-span-2 space-y-6">
            <TodayScheduleTable
              appointments={todayAppointments}
              completingId={completingId}
              onComplete={scheduleFromApi ? handleComplete : undefined}
            />
          </div>
          <div className="space-y-6">
            <PatientQueuePanel queue={data.patientQueue} />
            <PendingLabsPanel labs={data.pendingLabReviews} />
          </div>
        </div>
    </div>
  );
}
