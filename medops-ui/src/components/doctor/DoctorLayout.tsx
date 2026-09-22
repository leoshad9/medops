import { useCallback, useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import { useAuth } from "../../context/useAuth";
import { useNotifications } from "../../hooks/useNotifications";
import { DoctorHeader } from "./DoctorHeader";
import { DoctorNotificationDropdown } from "./DoctorNotificationDropdown";
import { DoctorSidebar } from "./DoctorSidebar";
import { DOCTOR_VIEW_METADATA, doctorViewFromPath } from "../../lib/doctorRoutes";
import { getMyDoctorProfile } from "../../services/doctorService";
import type { DoctorProfile } from "../../types/doctor";
import type { NotificationItem } from "../../types/patient";

export interface DoctorLayoutContext {
  profile: DoctorProfile | null;
  notifications: NotificationItem[];
  unreadNotificationCount: number;
  notificationsLoading: boolean;
  notificationsError: string | null;
  markNotificationRead: (notificationId: string) => Promise<void>;
  markAllNotificationsRead: () => Promise<void>;
}

export function DoctorLayout() {
  const location = useLocation();
  // A page-load restore receives the profile with the session from GET /v1/me, so the
  // header can render immediately; sessions established by login/registration still
  // fetch it below.
  const { doctorProfile: bootstrapProfile } = useAuth();
  const [profile, setProfile] = useState<DoctorProfile | null>(bootstrapProfile);
  const [profileError, setProfileError] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const view = doctorViewFromPath(location.pathname);
  const meta = DOCTOR_VIEW_METADATA[view];
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
    return getMyDoctorProfile()
      .then((fetched) => {
        setProfile(fetched);
      })
      .catch(() => {
        setProfileError(true);
      });
  }, []);

  useEffect(() => {
    if (bootstrapProfile) {
      return;
    }
    loadProfile(); // oxlint-disable-line react/set-state-in-effect
  }, [loadProfile, bootstrapProfile]);

  return (
    <div className="flex min-h-dvh bg-brand-paper font-brand-sans text-brand-ink">
      <DoctorSidebar />
      <main className="min-h-0 flex-1 overflow-y-auto space-y-6 p-4 sm:p-6 md:p-8 lg:p-10 xl:p-12 max-w-full w-full lg:max-w-7xl xl:max-w-6xl mx-auto container-main safe-top safe-bottom">
        {profile ? (
          <DoctorHeader
            profile={profile}
            unreadAlertsCount={unreadCount}
            onToggleNotifications={() => setShowNotifications((prev) => !prev)}
            title={meta.title}
            subtitle={meta.subtitle}
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
        {showNotifications && profile && (
          <DoctorNotificationDropdown
            notifications={notifications}
            unreadCount={unreadCount}
            loading={notificationsLoading}
            onMarkRead={markNotificationRead}
            onMarkAllRead={markAllNotificationsRead}
            onClose={() => setShowNotifications(false)}
          />
        )}
        <Outlet
          context={{
            profile,
            notifications,
            unreadNotificationCount: unreadCount,
            notificationsLoading,
            notificationsError,
            markNotificationRead,
            markAllNotificationsRead,
          }}
        />
      </main>
    </div>
  );
}
