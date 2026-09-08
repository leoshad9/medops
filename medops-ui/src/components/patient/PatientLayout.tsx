import { useEffect, useRef, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

import { useNotifications } from "../../hooks/useNotifications";
import { PATIENT_PATHS, PATIENT_VIEW_METADATA, patientViewFromPath } from "../../lib/patientRoutes";
import { mockPatientDashboard } from "../../pages/patient/mockDashboardData";
import { getMyProfile } from "../../services/patientService";
import type {
  NotificationItem,
  PatientDashboardData,
  PatientProfile,
} from "../../types/patient";
import { PatientHeader } from "./PatientHeader";
import { PatientSidebar } from "./PatientSidebar";

export interface PatientPortalContext {
  data: PatientDashboardData;
  profile: PatientProfile;
  patientId: string | null;
  notifications: NotificationItem[];
  unreadNotificationCount: number;
  notificationsLoading: boolean;
  notificationsError: string | null;
  markNotificationRead: (notificationId: string) => Promise<void>;
  markAllNotificationsRead: () => Promise<void>;
}

export function PatientLayout() {
  const data = mockPatientDashboard;
  const location = useLocation();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<PatientProfile | null>(null);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const mainRef = useRef<HTMLElement>(null);
  const {
    notifications,
    unreadCount,
    loading: notificationsLoading,
    error: notificationsError,
    markRead: markNotificationRead,
    markAllRead: markAllNotificationsRead,
  } = useNotifications();

  useEffect(() => {
    let cancelled = false;
    getMyProfile()
      .then((fetched) => {
        if (!cancelled) {
          setProfile(fetched);
        }
      })
      .catch(() => {
        // Fallback to mock header data if backend endpoint is unavailable
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    mainRef.current?.scrollTo({ top: 0, behavior: "smooth" });
  }, [location.pathname]);

  const activeView = patientViewFromPath(location.pathname);
  const meta = PATIENT_VIEW_METADATA[activeView];
  const resolvedProfile = profile ?? data.profile;

  return (
    <div className="flex h-dvh overflow-hidden bg-brand-paper font-brand-sans text-brand-ink">
      <PatientSidebar mobileOpen={mobileSidebarOpen} onCloseMobile={() => setMobileSidebarOpen(false)} />

      <main ref={mainRef} className="min-h-0 flex-1 overflow-y-auto space-y-6 p-4 sm:p-8 max-w-7xl w-full">
        <PatientHeader
          profile={resolvedProfile}
          unreadNotificationCount={unreadCount}
          title={meta.title}
          subtitle={meta.subtitle}
          onOpenMobileMenu={() => setMobileSidebarOpen(true)}
          onViewNotifications={() => void navigate(PATIENT_PATHS.notifications)}
        />

        <div className="animate-in fade-in duration-150">
          <Outlet
            context={
              {
                data,
                profile: resolvedProfile,
                patientId: profile?.id ?? null,
                notifications,
                unreadNotificationCount: unreadCount,
                notificationsLoading,
                notificationsError,
                markNotificationRead,
                markAllNotificationsRead,
              } satisfies PatientPortalContext
            }
          />
        </div>
      </main>
    </div>
  );
}
