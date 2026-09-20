import { useCallback, useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import { useNotifications } from "../../hooks/useNotifications";
import { DoctorHeader } from "./DoctorHeader";
import { DoctorSidebar } from "./DoctorSidebar";
import { DOCTOR_VIEW_METADATA, doctorViewFromPath } from "../../lib/doctorRoutes";
import { getMyDoctorProfile } from "../../services/doctorService";
import type { DoctorProfile } from "../../types/doctor";

export function DoctorLayout() {
  const location = useLocation();
  const [profile, setProfile] = useState<DoctorProfile | null>(null);
  const [profileError, setProfileError] = useState(false);
  const view = doctorViewFromPath(location.pathname);
  const meta = DOCTOR_VIEW_METADATA[view];
  const { unreadCount } = useNotifications();

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
    loadProfile(); // oxlint-disable-line react/set-state-in-effect
  }, [loadProfile]);

  return (
    <div className="flex min-h-dvh bg-brand-paper font-brand-sans text-brand-ink">
      <DoctorSidebar />
      <main className="min-h-0 flex-1 overflow-y-auto space-y-6 p-4 sm:p-6 md:p-8 lg:p-10 xl:p-12 max-w-full w-full lg:max-w-7xl xl:max-w-6xl mx-auto container-main safe-top safe-bottom">
        {profile ? (
          <DoctorHeader
            profile={profile}
            unreadAlertsCount={unreadCount}
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
        <Outlet />
      </main>
    </div>
  );
}
