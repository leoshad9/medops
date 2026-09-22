import { Clock3 } from "lucide-react";
import { useMemo } from "react";

import { getTimeOfDayGreeting, getUserTimeZone } from "../../lib/greeting";

interface DashboardGreetingProps {
  message: string;
  name?: string;
}

export function DashboardGreeting({ message, name }: Readonly<DashboardGreetingProps>) {
  const timeZone = useMemo(() => getUserTimeZone(), []);
  const greeting = getTimeOfDayGreeting(new Date(), timeZone);
  const displayName = name?.trim();

  return (
    <section
      className="rounded-2xl border border-brand-line bg-gradient-to-br from-brand-primary-tint to-white p-4 shadow-xs sm:p-5"
      aria-label="Dashboard greeting"
    >
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="fluid-text-base font-bold text-brand-ink">
          {greeting}{displayName ? `, ${displayName}` : ""}
        </p>
        <div className="flex items-center gap-1.5 text-xs font-semibold text-brand-primary-dark">
          <Clock3 className="h-4 w-4" aria-hidden="true" />
          <span className="font-brand-mono">{timeZone}</span>
        </div>
      </div>
      <p className="mt-1 fluid-text-sm text-brand-muted">{message}</p>
    </section>
  );
}
