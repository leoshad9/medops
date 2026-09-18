import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import { useNotifications } from "../../hooks/useNotifications";
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
  const { unreadCount } = useNotifications();

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
    <div className="flex min-h-dvh bg-brand-paper font-brand-sans text-brand-ink">
      <DoctorSidebar />
      <main className="min-h-0 flex-1 overflow-y-auto space-y-6 p-4 sm:p-6 md:p-8 lg:p-10 xl:p-12 max-w-full w-full lg:max-w-7xl xl:max-w-6xl mx-auto container-main safe-top safe-bottom">
        <DoctorHeader
          profile={profile ?? mockDoctorDashboard.profile}
          unreadAlertsCount={unreadCount}
          title={meta.title}
          subtitle={meta.subtitle}
        />
        <Outlet />
      </main>
    </div>
  );
}
