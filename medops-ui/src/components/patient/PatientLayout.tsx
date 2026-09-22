import { useCallback, useEffect, useRef, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import { useNotifications } from "../../hooks/useNotifications";
import { PATIENT_VIEW_METADATA, patientViewFromPath } from "../../lib/patientRoutes";
import { getMyProfile } from "../../services/patientService";
import type {
  NotificationItem,
  PatientProfile,
} from "../../types/patient";
import { MedOpsAIFloatingButton } from "../ai/MedOpsAIFloatingButton";
import { MedOpsAIChatPanel } from "../ai/MedOpsAIChatPanel";
import { PatientHeader } from "./PatientHeader";
import { PatientSidebar } from "./PatientSidebar";

export interface PatientPortalContext {
  profile: PatientProfile | null;
  patientId: string | null;
  notifications: NotificationItem[];
  unreadNotificationCount: number;
  notificationsLoading: boolean;
  notificationsError: string | null;
  markNotificationRead: (notificationId: string) => Promise<void>;
  markAllNotificationsRead: () => Promise<void>;
}

/** Provides the patient portal shell and opens its AI assistant from UI or window events. */
export function PatientLayout() {
  const location = useLocation();
  const [profile, setProfile] = useState<PatientProfile | null>(null);
  const [profileError, setProfileError] = useState(false);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [aiChatOpen, setAiChatOpen] = useState(false);
  const mainRef = useRef<HTMLElement>(null);
  const {
    notifications,
    unreadCount,
    loading: notificationsLoading,
    error: notificationsError,
    markRead: markNotificationRead,
    markAllRead: markAllNotificationsRead,
  } = useNotifications();

  const loadProfile = useCallback(() => {
    setProfileError(false);
    return getMyProfile()
      .then((fetched) => setProfile(fetched))
      .catch(() => setProfileError(true));
  }, []);

  useEffect(() => {
    loadProfile(); // oxlint-disable-line react/set-state-in-effect
  }, [loadProfile]);

  useEffect(() => {
    mainRef.current?.scrollTo({ top: 0, behavior: "smooth" });
  }, [location.pathname]);

  useEffect(() => {
    const handler = () => setAiChatOpen(true);
    window.addEventListener("medops:open-ai-chat", handler);
    return () => window.removeEventListener("medops:open-ai-chat", handler);
  }, []);

  const activeView = patientViewFromPath(location.pathname);
  const meta = PATIENT_VIEW_METADATA[activeView];
  const firstName = profile?.name?.split(" ")[0] ?? "there";

  return (
    <div className="flex min-h-dvh bg-brand-paper font-brand-sans text-brand-ink">
      <PatientSidebar
        mobileOpen={mobileSidebarOpen}
        onCloseMobile={() => setMobileSidebarOpen(false)}
      />

      <main
        ref={mainRef}
        className="min-h-0 flex-1 overflow-y-auto space-y-6 p-4 sm:p-6 md:p-8 lg:p-10 xl:p-12 max-w-full w-full lg:max-w-7xl xl:max-w-6xl mx-auto container-main safe-top safe-bottom"
      >
        {profile ? (
          <PatientHeader
            profile={profile}
            unreadNotificationCount={unreadCount}
            title={meta.title}
            subtitle={meta.subtitle}
            onOpenMobileMenu={() => setMobileSidebarOpen(true)}
            notifications={notifications}
            notificationsLoading={notificationsLoading}
            onMarkRead={markNotificationRead}
            onMarkAllRead={markAllNotificationsRead}
          />
        ) : (
          <div className="border-b border-brand-line pb-6">
            {profileError ? (
              <div className="flex items-center justify-between gap-4">
                <p className="text-sm text-brand-rust">
                  Unable to load your profile. Header details are unavailable.
                </p>
                <button
                  type="button"
                  onClick={() => void loadProfile()}
                  className="rounded-lg border border-brand-line bg-white px-3 py-1.5 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary hover:text-white"
                >
                  Retry
                </button>
              </div>
            ) : (
              <p className="text-sm text-brand-muted">Loading profile…</p>
            )}
          </div>
        )}

        <div className="animate-in fade-in duration-150">
          <Outlet
            context={
              {
                profile,
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

      <MedOpsAIFloatingButton onClick={() => setAiChatOpen(true)} />
      <MedOpsAIChatPanel
        isOpen={aiChatOpen}
        onClose={() => setAiChatOpen(false)}
        firstName={firstName}
      />
    </div>
  );
}
