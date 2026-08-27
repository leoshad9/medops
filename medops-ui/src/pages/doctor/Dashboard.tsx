import { useEffect, useState } from "react";

import { DoctorHeader } from "../../components/doctor/DoctorHeader";
import { DoctorSidebar } from "../../components/doctor/DoctorSidebar";
import { DoctorStatCards } from "../../components/doctor/DoctorStatCards";
import { PatientQueuePanel } from "../../components/doctor/PatientQueuePanel";
import { PendingLabsPanel } from "../../components/doctor/PendingLabsPanel";
import { TodayScheduleTable } from "../../components/doctor/TodayScheduleTable";
import { getMyDoctorProfile } from "../../services/doctorService";
import type { DoctorProfile } from "../../types/doctor";
import { mockDoctorDashboard } from "./mockDoctorData";

export function DoctorDashboard() {
  const data = mockDoctorDashboard;
  const [profile, setProfile] = useState<DoctorProfile | null>(null);

  useEffect(() => {
    let cancelled = false;
    getMyDoctorProfile()
      .then((fetched) => {
        if (!cancelled) {
          setProfile(fetched);
        }
      })
      .catch(() => {
        // Fallback to mock profile data if backend is offline or during local testing
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex min-h-screen bg-brand-paper font-brand-sans text-brand-ink">
      <DoctorSidebar />

      <main className="flex-1 space-y-6 p-8">
        <DoctorHeader
          profile={profile ?? data.profile}
          unreadAlertsCount={data.unreadAlertsCount}
        />

        <DoctorStatCards stats={data.stats} />

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
          <div className="xl:col-span-2 space-y-6">
            <TodayScheduleTable appointments={data.todayAppointments} />
          </div>
          <div className="space-y-6">
            <PatientQueuePanel queue={data.patientQueue} />
            <PendingLabsPanel labs={data.pendingLabReviews} />
          </div>
        </div>
      </main>
    </div>
  );
}
