import { Activity, Clock, FileCheck2, TrendingUp, Users } from "lucide-react";

import type { DoctorDashboardStat } from "../../types/doctor";

interface DoctorStatCardsProps {
  stats: DoctorDashboardStat[];
}

const STAT_ICONS: Record<string, typeof Users> = {
  "today-patients": Users,
  "waiting-room": Clock,
  "lab-results": FileCheck2,
  satisfaction: Activity,
};

export function DoctorStatCards({ stats }: Readonly<DoctorStatCardsProps>) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {stats.map((stat) => {
        const Icon = STAT_ICONS[stat.id] ?? TrendingUp;
        return (
          <div
            key={stat.id}
            className="group relative flex flex-col justify-between overflow-hidden rounded-xl border border-brand-line bg-white p-5 shadow-xs transition hover:border-brand-primary/40 hover:shadow-md"
          >
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-brand-muted">
                  {stat.label}
                </p>
                <p className="mt-1 text-2xl font-bold text-brand-ink">{stat.value}</p>
              </div>
              <div className="grid h-10 w-10 place-items-center rounded-lg bg-brand-primary-tint text-brand-primary-dark transition group-hover:scale-105">
                <Icon className="h-5 w-5" />
              </div>
            </div>

            <div className="mt-4 flex items-center justify-between border-t border-brand-line/60 pt-3 text-xs">
              <span className="text-brand-muted">{stat.sublabel}</span>
              {stat.trend && (
                <span
                  className={`font-semibold ${
                    stat.trendPositive ? "text-emerald-700" : "text-amber-700"
                  }`}
                >
                  {stat.trend}
                </span>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
