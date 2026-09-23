import { Link } from "react-router-dom";

import type { DoctorDashboardStat } from "../../types/doctor";

interface DoctorStatCardsProps {
  stats: DoctorDashboardStat[];
}

export function DoctorStatCards({ stats }: Readonly<DoctorStatCardsProps>) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {stats.map((stat) => {
        const Icon = stat.icon;
        return (
          <Link
            key={stat.id}
            to={stat.to}
            className="group relative flex flex-col justify-between overflow-hidden rounded-xl border border-brand-line bg-white p-5 shadow-xs no-underline text-inherit transition hover:border-brand-primary/40 hover:shadow-md focus-visible-ring"
            data-testid={`stat-card-${stat.id}`}
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
          </Link>
        );
      })}
    </div>
  );
}
