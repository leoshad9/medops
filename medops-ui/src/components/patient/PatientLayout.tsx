import { useEffect, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

import { PATIENT_PATHS, PATIENT_VIEW_METADATA, patientViewFromPath } from "../../lib/patientRoutes";
import { mockPatientDashboard } from "../../pages/patient/mockDashboardData";
import { getMyProfile } from "../../services/patientService";
import type { PatientDashboardData, PatientProfile } from "../../types/patient";
import { PatientHeader } from "./PatientHeader";
import { PatientSidebar } from "./PatientSidebar";

export interface PatientPortalContext {
  data: PatientDashboardData;
  profile: PatientProfile;
}

export function PatientLayout() {
  const data = mockPatientDashboard;
  const location = useLocation();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<PatientProfile | null>(null);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getMyProfile()
      .then((fetched) => {
        if (!cancelled) {
          setProfile(fetched);
        }
      })
      .catch(() => {
        // Fallback to mock data if backend endpoint is unavailable
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [location.pathname]);

  const activeView = patientViewFromPath(location.pathname);
  const meta = PATIENT_VIEW_METADATA[activeView];
  const resolvedProfile = profile ?? data.profile;

  return (
    <div className="flex min-h-screen bg-brand-paper font-brand-sans text-brand-ink">
      <PatientSidebar mobileOpen={mobileSidebarOpen} onCloseMobile={() => setMobileSidebarOpen(false)} />

      <main className="flex-1 space-y-6 p-4 sm:p-8 max-w-7xl w-full">
        <PatientHeader
          profile={resolvedProfile}
          unreadNotificationCount={0}
          title={meta.title}
          subtitle={meta.subtitle}
          onOpenMobileMenu={() => setMobileSidebarOpen(true)}
          onViewNotifications={() => void navigate(PATIENT_PATHS.dashboard)}
        />

        <div className="animate-in fade-in duration-150">
          <Outlet context={{ data, profile: resolvedProfile } satisfies PatientPortalContext} />
        </div>
      </main>
    </div>
  );
}
