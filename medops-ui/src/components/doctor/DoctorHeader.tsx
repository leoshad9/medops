import { Bell, ShieldCheck, Stethoscope } from "lucide-react";

import { getTimeOfDayGreeting } from "../../lib/greeting";
import type { DoctorProfile } from "../../types/doctor";

interface DoctorHeaderProps {
  profile: DoctorProfile;
  unreadAlertsCount: number;
}

function initialsOf(name: string): string {
  const clean = name.replace(/^Dr\.\s+/i, "");
  const parts = clean.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? (parts.at(-1)?.[0] ?? "") : "";
  return (first + last).toUpperCase() || "DR";
}

export function DoctorHeader({ profile, unreadAlertsCount }: Readonly<DoctorHeaderProps>) {
  const greeting = getTimeOfDayGreeting();

  return (
    <div className="relative flex flex-col justify-between gap-4 border-b border-brand-line pb-6 sm:flex-row sm:items-center">
      <div>
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
            {"Duty Active"}
          </span>
          <span className="inline-flex items-center gap-1 text-xs text-brand-muted">
            <Stethoscope className="h-3.5 w-3.5" />
            {profile.specialty}
          </span>
        </div>
        <h1 className="mt-1 text-2xl font-bold tracking-tight text-brand-ink">
          {greeting}, {profile.name}
        </h1>
        <p className="mt-0.5 text-sm text-brand-muted">
          Clinical Command Center · {profile.specialty} Division
        </p>
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          className="relative grid h-10 w-10 place-items-center rounded-lg border border-brand-line bg-white text-brand-muted transition hover:text-brand-ink"
          aria-label="Clinical Alerts"
        >
          <Bell className="h-4 w-4" />
          {unreadAlertsCount > 0 && (
            <span className="absolute top-2 right-2 h-2 w-2 rounded-full bg-brand-rust" />
          )}
        </button>

        <div className="flex items-center gap-3 rounded-lg border border-brand-line bg-white p-1.5 pr-4 shadow-xs">
          <div className="grid h-9 w-9 place-items-center rounded-full bg-brand-primary text-sm font-bold text-white">
            {initialsOf(profile.name)}
          </div>
          <div className="text-left">
            <div className="flex items-center gap-1">
              <p className="text-sm font-semibold text-brand-ink">{profile.name}</p>
              <ShieldCheck className="h-3.5 w-3.5 text-blue-600" />
            </div>
            <p className="font-brand-mono text-xs text-brand-muted">License: {profile.licenseNumber}</p>
          </div>
        </div>
      </div>
    </div>
  );
}
