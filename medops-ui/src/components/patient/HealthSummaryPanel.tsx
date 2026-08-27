import { Droplet, Heart, Weight } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import type { HealthMetric } from "../../types/patient";

const ICONS_BY_ID: Record<string, LucideIcon> = {
  bp: Heart,
  "blood-group": Droplet,
  weight: Weight,
};

interface HealthSummaryPanelProps {
  metrics: HealthMetric[];
}

export function HealthSummaryPanel({ metrics }: Readonly<HealthSummaryPanelProps>) {
  return (
    <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
      <div className="flex items-center justify-between">
        <h2 className="font-bold text-brand-ink text-base">Health Summary</h2>
        <svg viewBox="0 0 80 22" fill="none" aria-hidden="true" className="w-16 h-4 text-brand-primary opacity-70">
          <path
            d="M0 11 H24 L28 3 L36 19 L42 7 L46 11 H80"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="pulseline-path"
          />
        </svg>
      </div>

      <div className="mt-3 divide-y divide-brand-line/60">
        {metrics.map((metric) => {
          const Icon = ICONS_BY_ID[metric.id] ?? Heart;

          return (
            <div key={metric.id} className="flex items-center justify-between py-2.5 first:pt-0 last:pb-0">
              <span className="flex items-center gap-2.5 text-xs font-medium text-slate-700">
                <Icon className="h-3.5 w-3.5 text-brand-primary" />
                {metric.label}
              </span>
              <span className="font-brand-mono text-xs font-bold text-brand-ink">{metric.value}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

