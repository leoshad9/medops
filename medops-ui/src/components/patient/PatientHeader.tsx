import { useState } from "react";
import { Bell, Menu } from "lucide-react";

import { getTimeOfDayGreeting, getUserTimeZone } from "../../lib/greeting";
import { PATIENT_PATHS } from "../../lib/patientRoutes";
import type { PatientProfile, NotificationItem } from "../../types/patient";
import { NotificationDropdown } from "./NotificationDropdown";

interface PatientHeaderProps {
  profile: PatientProfile;
  unreadNotificationCount: number;
  title?: string;
  subtitle?: string;
  onOpenMobileMenu?: () => void;
  notifications: NotificationItem[];
  notificationsLoading: boolean;
  onMarkRead: (notificationId: string) => Promise<void>;
  onMarkAllRead: () => Promise<void>;
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? (parts.at(-1)?.[0] ?? "") : "";
  return (first + last).toUpperCase();
}

export function PatientHeader({
  profile,
  unreadNotificationCount,
  title,
  subtitle,
  onOpenMobileMenu,
  notifications,
  notificationsLoading,
  onMarkRead,
  onMarkAllRead,
}: Readonly<PatientHeaderProps>) {
  const firstName = profile.name.split(" ")[0];
  const timeZone = getUserTimeZone();
  const greeting = getTimeOfDayGreeting(new Date(), timeZone);

  const displayTitle = title ?? `${greeting}, ${firstName}`;
  const displaySubtitle = subtitle ?? "Here's your health overview and upcoming appointments.";

  const [showNotifications, setShowNotifications] = useState(false);

  const toggleNotifications = () => setShowNotifications((prev) => !prev);
  const closeNotifications = () => setShowNotifications(false);

  return (
    <header className="relative flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 sm:gap-4 border-b border-brand-line pb-4 sm:pb-5">
      <div className="relative">
        <div className="flex items-center gap-2 sm:gap-3">
          {onOpenMobileMenu && (
            <button
              type="button"
              onClick={onOpenMobileMenu}
              className="grid h-9 w-9 place-items-center rounded-lg border border-brand-line bg-white text-brand-muted transition hover:text-brand-ink lg:hidden cursor-pointer touch-target focus-visible-ring"
              aria-label="Open menu"
            >
              <Menu className="h-5 w-5" />
            </button>
          )}
          <div>
            <h1 className="fluid-text-xl lg:fluid-text-2xl font-bold tracking-tight text-brand-ink">
              {displayTitle}
            </h1>
            <p className="mt-1 fluid-text-sm text-brand-muted">
              {displaySubtitle}
            </p>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2 sm:gap-3 shrink-0 relative">
        <button
          type="button"
          onClick={toggleNotifications}
          className="relative grid h-9 w-9 place-items-center rounded-lg border border-brand-line bg-white text-brand-muted transition hover:text-brand-ink hover:border-brand-primary cursor-pointer touch-target focus-visible-ring"
          aria-label="Notifications"
          aria-expanded={showNotifications}
          aria-haspopup="true"
        >
          <Bell className="h-4 w-4" />
          {unreadNotificationCount > 0 && (
            <span className="absolute top-2 right-2 h-2 w-2 rounded-full bg-brand-rust" />
          )}
        </button>

        {showNotifications && (
          <NotificationDropdown
            notifications={notifications}
            unreadCount={unreadNotificationCount}
            loading={notificationsLoading}
            viewAllPath={PATIENT_PATHS.notifications}
            onMarkRead={onMarkRead}
            onMarkAllRead={onMarkAllRead}
            onClose={closeNotifications}
          />
        )}

        <div className="flex items-center gap-2 sm:gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-full bg-brand-primary text-sm font-bold text-white shadow-sm">
            {initialsOf(profile.name)}
          </div>
          <div className="hidden text-left sm:block">
            <p className="fluid-text-sm font-semibold text-brand-ink leading-tight">{profile.name}</p>
            <p className="font-brand-mono fluid-text-xs text-brand-muted mt-0.5">{profile.mrn}</p>
          </div>
        </div>
      </div>
    </header>
  );
}


