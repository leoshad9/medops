import { AlertCircle, ArrowUpRight, CheckCircle, FlaskConical } from "lucide-react";

import type { PendingLabReview } from "../../types/doctor";

interface PendingLabsPanelProps {
  labs: PendingLabReview[];
}

export function PendingLabsPanel({ labs }: Readonly<PendingLabsPanelProps>) {
  return (
    <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
      <div className="flex items-center justify-between border-b border-brand-line pb-3">
        <div className="flex items-center gap-2">
          <FlaskConical className="h-4 w-4 text-brand-primary" />
          <h2 className="text-sm font-bold text-brand-ink">Diagnostic & Lab Reviews</h2>
        </div>
        <button
          type="button"
          className="inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
        >
          All Labs
          <ArrowUpRight className="h-3 w-3" />
        </button>
      </div>

      <div className="mt-4 space-y-3">
        {labs.map((lab) => (
          <div
            key={lab.id}
            className="flex items-center justify-between rounded-lg border border-brand-line/70 bg-slate-50/40 p-3 transition hover:bg-white hover:border-brand-primary/40"
          >
            <div>
              <div className="flex items-center gap-2">
                <p className="text-xs font-bold text-brand-ink">{lab.patientName}</p>
                {lab.status === "CRITICAL" && (
                  <span className="inline-flex items-center gap-1 rounded bg-red-100 px-1.5 py-0.5 text-[10px] font-bold text-red-700">
                    <AlertCircle className="h-3 w-3" /> Critical Values
                  </span>
                )}
                {lab.status === "ABNORMAL" && (
                  <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-800">
                    Abnormal
                  </span>
                )}
                {lab.status === "NORMAL" && (
                  <span className="inline-flex items-center gap-0.5 rounded bg-emerald-50 px-1.5 py-0.5 text-[10px] font-semibold text-emerald-700">
                    <CheckCircle className="h-3 w-3" /> Normal
                  </span>
                )}
              </div>
              <p className="mt-0.5 text-xs text-slate-600 font-medium">{lab.testName}</p>
              <p className="text-[11px] text-brand-muted">Ordered: {lab.orderedDate}</p>
            </div>

            <button
              type="button"
              className="rounded-md border border-brand-line bg-white px-2.5 py-1 text-xs font-semibold text-brand-primary-dark hover:bg-brand-primary hover:text-white transition"
            >
              Review
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
