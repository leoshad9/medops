import { Bell, LogOut, Menu } from "lucide-react";

import { useAuth } from "../../context/useAuth";
import { getTimeOfDayGreeting } from "../../lib/greeting";
import type { PatientProfile } from "../../types/patient";

interface PatientHeaderProps {
  profile: PatientProfile;
  unreadNotificationCount: number;
  title?: string;
  subtitle?: string;
  onOpenMobileMenu?: () => void;
  onViewNotifications?: () => void;
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
  onViewNotifications,
}: Readonly<PatientHeaderProps>) {
  const { logout } = useAuth();
  const firstName = profile.name.split(" ")[0];
  const greeting = getTimeOfDayGreeting();

  const displayTitle = title ?? `${greeting}, ${firstName}`;
  const displaySubtitle = subtitle ?? "Here's your health overview and upcoming appointments.";

  return (
    <header className="relative flex items-start justify-between gap-4 pb-4">
      <div className="relative">
        <div className="flex items-center gap-3">
          {onOpenMobileMenu && (
            <button
              type="button"
              onClick={onOpenMobileMenu}
              className="grid h-9 w-9 place-items-center rounded-lg border border-brand-line bg-white text-brand-muted transition hover:text-brand-ink lg:hidden cursor-pointer"
              aria-label="Open menu"
            >
              <Menu className="h-5 w-5" />
            </button>
          )}
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-brand-ink">
              {displayTitle}
            </h1>
            <p className="mt-1 text-sm text-brand-muted">
              {displaySubtitle}
            </p>
          </div>
        </div>

        {/* ECG pulse line animation matching wireframe */}
        <div className="mt-2 w-56 h-4 text-brand-primary opacity-60">
          <svg viewBox="0 0 220 20" fill="none" aria-hidden="true" className="w-full h-full">
            <path
              d="M0 10 H70 L80 2 L92 18 L102 6 L110 10 H220"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="pulseline-path"
            />
          </svg>
        </div>
      </div>

      <div className="flex items-center gap-3 shrink-0">
        <button
          type="button"
          onClick={onViewNotifications}
          className="relative grid h-9 w-9 place-items-center rounded-lg border border-brand-line bg-white text-brand-muted transition hover:text-brand-ink hover:border-brand-primary cursor-pointer"
          aria-label="Notifications"
        >
          <Bell className="h-4 w-4" />
          {unreadNotificationCount > 0 && (
            <span className="absolute top-2 right-2 h-2 w-2 rounded-full bg-brand-rust" />
          )}
        </button>

        <div className="flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-full bg-brand-primary text-sm font-bold text-white shadow-sm">
            {initialsOf(profile.name)}
          </div>
          <div className="hidden text-left sm:block">
            <p className="text-sm font-semibold text-brand-ink leading-tight">{profile.name}</p>
            <p className="font-brand-mono text-xs text-brand-muted mt-0.5">{profile.mrn}</p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => void logout()}
          className="flex items-center gap-1.5 rounded-lg border border-brand-line bg-white px-2.5 py-2 text-xs font-semibold text-slate-600 transition hover:bg-red-50 hover:text-red-700 hover:border-red-200 cursor-pointer shadow-2xs"
          title="Sign Out"
        >
          <LogOut className="h-3.5 w-3.5" />
          <span className="hidden md:inline">Sign Out</span>
        </button>
      </div>
    </header>
  );
}


