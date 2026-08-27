import { Link } from "react-router-dom";
import { Calendar, ChevronRight, FlaskConical, Folder, Pill } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { PATIENT_PATHS, type PatientViewKey } from "../../lib/patientRoutes";
import type { DashboardStat } from "../../types/patient";

const ICONS_BY_ID: Record<string, LucideIcon> = {
  "next-appointment": Calendar,
  prescriptions: Pill,
  "lab-reports": FlaskConical,
  "medical-records": Folder,
};

const VIEW_BY_STAT_ID: Record<string, PatientViewKey> = {
  "next-appointment": "appointments",
  prescriptions: "prescriptions",
  "lab-reports": "labs",
  "medical-records": "records",
};

const CHIP_STYLES = ["bg-brand-primary-tint text-brand-primary-dark", "bg-brand-amber-tint text-brand-amber"];

interface StatCardsRowProps {
  stats: DashboardStat[];
}

export function StatCardsRow({ stats }: Readonly<StatCardsRowProps>) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {stats.map((stat, index) => {
        const Icon = ICONS_BY_ID[stat.id] ?? Calendar;
        const chip = CHIP_STYLES[index % CHIP_STYLES.length];
        const targetView = VIEW_BY_STAT_ID[stat.id];

        return (
          <div key={stat.id} className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs transition hover:border-brand-primary/40">
            <div className="flex items-start justify-between">
              <span className="text-xs font-semibold text-brand-muted">{stat.label}</span>
              <div className={`flex h-8 w-8 items-center justify-center rounded-lg ${chip}`}>
                <Icon className="h-4 w-4" />
              </div>
            </div>
            <p className="mt-3 font-brand-mono text-2xl font-bold tracking-tight text-brand-ink">{stat.value}</p>
            <p className="text-xs text-brand-muted mt-0.5">{stat.sublabel}</p>
            {targetView && (
              <Link
                to={PATIENT_PATHS[targetView]}
                className="mt-3.5 flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline cursor-pointer group"
              >
                <span>{stat.linkLabel}</span>
                <ChevronRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
              </Link>
            )}
          </div>
        );
      })}
    </div>
  );
}
