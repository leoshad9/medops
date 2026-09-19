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

// One shared badge style (40x40, 12px radius, teal tint) keeps the four
// overview cards visually consistent instead of alternating shapes/tints.
const ICON_BADGE =
  "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-primary-tint text-brand-primary-dark";

const CARD_BASE =
  "group flex h-full flex-col rounded-2xl border border-brand-line bg-white p-5 shadow-xs transition duration-150 hover:-translate-y-0.5 hover:border-brand-primary/40 hover:shadow-sm focus-visible-ring";

interface StatCardsRowProps {
  stats: DashboardStat[];
}

export function StatCardsRow({ stats }: Readonly<StatCardsRowProps>) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {stats.map((stat) => {
        const Icon = ICONS_BY_ID[stat.id] ?? Calendar;
        const targetView = VIEW_BY_STAT_ID[stat.id];

        const cardBody = (
          <>
            <div className="flex items-start justify-between gap-3">
              <span className="text-xs font-semibold text-brand-muted">{stat.label}</span>
              <span className={ICON_BADGE}>
                <Icon className="h-5 w-5" />
              </span>
            </div>
            {/* Standardized metric: 24px bold with a fixed baseline rhythm. */}
            <p className="mt-3 font-brand-mono text-2xl font-bold leading-none tracking-tight text-brand-ink">
              {stat.value}
            </p>
            <p className="mt-1.5 text-xs text-brand-muted">{stat.sublabel}</p>
            {targetView && (
              <span className="mt-3.5 flex items-center gap-1 text-xs font-semibold text-brand-primary-dark group-hover:underline">
                <span>{stat.linkLabel}</span>
                <ChevronRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
              </span>
            )}
          </>
        );

        // The whole card is the click target; the "View details" row is a
        // visual affordance rather than a separate small link.
        return targetView ? (
          <Link key={stat.id} to={PATIENT_PATHS[targetView]} className={`${CARD_BASE} cursor-pointer`}>
            {cardBody}
          </Link>
        ) : (
          <div key={stat.id} className={CARD_BASE}>
            {cardBody}
          </div>
        );
      })}
    </div>
  );
}
