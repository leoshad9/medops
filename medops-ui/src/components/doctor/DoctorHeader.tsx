import { useEffect, useRef, useState } from "react";
import { Bell, LogOut, User } from "lucide-react";
import { Link } from "react-router-dom";

import { CLINIC_TIMEZONE } from "../../lib/clinicTime";
import { getTimeOfDayGreeting } from "../../lib/greeting";
import { DOCTOR_PATHS } from "../../lib/doctorRoutes";
import { DutyStatusSelector } from "./DutyStatusSelector";
import type { DoctorDutyStatus, DoctorProfile } from "../../types/doctor";

interface DoctorHeaderProps {
  profile: DoctorProfile;
  unreadAlertsCount: number;
  title?: string;
  subtitle?: string;
  onToggleNotifications: () => void;
  onLogout: () => void;
  duty: {
    status: DoctorDutyStatus;
    onChange: (status: DoctorDutyStatus) => Promise<void>;
    needsConfirmation: (status: DoctorDutyStatus) => boolean;
    isChanging: boolean;
    error: string | null;
    pendingItems: string[];
  };
}

function initialsOf(name: string): string {
  const clean = name.replace(/^Dr\.\s+/i, "");
  const parts = clean.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? (parts.at(-1)?.[0] ?? "") : "";
  return (first + last).toUpperCase() || "DR";
}

export function DoctorHeader({
  profile,
  unreadAlertsCount,
  title,
  subtitle,
  onToggleNotifications,
  onLogout,
  duty,
}: Readonly<DoctorHeaderProps>) {
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const profileButtonRef = useRef<HTMLButtonElement>(null);
  const profileMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        profileMenuRef.current &&
        !profileMenuRef.current.contains(event.target as Node) &&
        profileButtonRef.current &&
        !profileButtonRef.current.contains(event.target as Node)
      ) {
        setIsProfileOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsProfileOpen(false);
      }
    }
    if (isProfileOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isProfileOpen]);

  const heading = title ?? `${getTimeOfDayGreeting(new Date(), CLINIC_TIMEZONE)}, ${profile.name}`;
  const sub = subtitle ?? `${profile.specialty} · ${profile.licenseNumber}`;

  return (
    <div className="flex flex-col gap-3 border-b border-brand-line pb-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <h1 className="fluid-text-xl lg:fluid-text-2xl font-bold tracking-tight text-brand-ink truncate">
          {heading}
        </h1>
        <p className="mt-0.5 fluid-text-sm text-brand-muted truncate">{sub}</p>
        <div className="mt-2 flex items-center gap-3">
          <DutyStatusSelector
            status={duty.status}
            onChange={duty.onChange}
            needsConfirmation={duty.needsConfirmation}
            isChanging={duty.isChanging}
            error={duty.error}
            pendingItems={duty.pendingItems}
          />
          <span
            className="fluid-text-xs text-brand-muted"
            aria-label={`Timezone: ${CLINIC_TIMEZONE}`}
          >
            · {CLINIC_TIMEZONE}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-2 shrink-0">
        <button
          type="button"
          onClick={onToggleNotifications}
          className="relative grid h-10 w-10 place-items-center rounded-lg border border-brand-line bg-white text-brand-muted transition hover:text-brand-ink hover:border-brand-primary cursor-pointer touch-target focus-visible-ring"
          aria-label="Notifications"
        >
          <Bell className="h-4 w-4" />
          {unreadAlertsCount > 0 && (
            <span
              className="absolute top-2 right-2 h-2 w-2 rounded-full bg-brand-rust"
              aria-label={`${unreadAlertsCount} unread`}
            />
          )}
        </button>

        <div className="relative">
          <button
            ref={profileButtonRef}
            type="button"
            onClick={() => setIsProfileOpen((prev) => !prev)}
            aria-haspopup="menu"
            aria-expanded={isProfileOpen}
            aria-label="Profile menu"
            className="flex items-center gap-2 rounded-lg border border-brand-line bg-white px-2.5 py-1.5 text-sm font-semibold text-brand-ink transition hover:bg-slate-50 focus-visible-ring"
          >
            <span className="grid h-8 w-8 place-items-center rounded-full bg-brand-primary text-xs font-bold text-white">
              {initialsOf(profile.name)}
            </span>
            <span className="hidden sm:inline">{profile.name}</span>
          </button>

          {isProfileOpen && (
            <div
              ref={profileMenuRef}
              className="absolute top-full right-0 z-30 mt-1 w-48 rounded-xl border border-brand-line bg-white shadow-lg"
              role="menu"
              aria-label="Profile menu"
            >
              <div className="py-1">
                <Link
                  to={DOCTOR_PATHS.profile}
                  className="flex items-center gap-2 px-4 py-2 text-sm text-brand-ink hover:bg-slate-50 focus-visible-ring"
                  role="menuitem"
                  onClick={() => setIsProfileOpen(false)}
                >
                  <User className="h-4 w-4" />
                  My Profile
                </Link>
                <button
                  type="button"
                  onClick={() => {
                    setIsProfileOpen(false);
                    void onLogout();
                  }}
                  className="flex w-full items-center gap-2 px-4 py-2 text-sm text-brand-ink hover:bg-slate-50 focus-visible-ring"
                  role="menuitem"
                >
                  <LogOut className="h-4 w-4" />
                  Sign Out
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
