import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import { DoctorHeader } from "./DoctorHeader";
import { DoctorSidebar } from "./DoctorSidebar";
import { DOCTOR_VIEW_METADATA, doctorViewFromPath } from "../../lib/doctorRoutes";
import { getMyDoctorProfile } from "../../services/doctorService";
import type { DoctorProfile } from "../../types/doctor";
import { mockDoctorDashboard } from "../../pages/doctor/mockDoctorData";

export function DoctorLayout() {
  const location = useLocation();
  const [profile, setProfile] = useState<DoctorProfile | null>(null);
  const view = doctorViewFromPath(location.pathname);
  const meta = DOCTOR_VIEW_METADATA[view];

  useEffect(() => {
    let cancelled = false;
    getMyDoctorProfile()
      .then((fetched) => {
        if (!cancelled) {
          setProfile(fetched);
        }
      })
      .catch(() => {
        // Keep mock header if the profile endpoint is unavailable.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex h-dvh overflow-hidden bg-brand-paper font-brand-sans text-brand-ink">
      <DoctorSidebar />
      <main className="min-h-0 flex-1 overflow-y-auto space-y-6 p-8">
        <DoctorHeader
          profile={profile ?? mockDoctorDashboard.profile}
          unreadAlertsCount={mockDoctorDashboard.unreadAlertsCount}
          title={meta.title}
          subtitle={meta.subtitle}
        />
        <Outlet />
      </main>
    </div>
  );
}
