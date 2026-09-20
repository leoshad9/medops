import { AlertCircle, ArrowUpRight, CheckCircle, FlaskConical, Hourglass } from "lucide-react";
import { Link } from "react-router-dom";

import { formatClinicDateTime } from "../../lib/clinicTime";
import { doctorPatientChartPath } from "../../lib/doctorRoutes";
import type { ClinicalReportDto } from "../../services/clinicalService";

interface PendingLabsPanelProps {
  labs: ClinicalReportDto[];
}

/**
 * Live pending-lab review panel fed by the dashboard aggregate. Only real
 * statuses exist (NEW / REVIEWED); the former mock CRITICAL/ABNORMAL badges
 * are gone until the domain grows result-level severity.
 */
export function PendingLabsPanel({ labs }: Readonly<PendingLabsPanelProps>) {
  return (
    <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
      <div className="flex items-center justify-between border-b border-brand-line pb-3">
        <div className="flex items-center gap-2">
          <FlaskConical className="h-4 w-4 text-brand-primary" />
          <h2 className="text-sm font-bold text-brand-ink">Diagnostic & Lab Reviews</h2>
        </div>
        <Link
          to="/doctor/labs"
          className="inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
        >
          All Labs
          <ArrowUpRight className="h-3 w-3" />
        </Link>
      </div>

      {labs.length === 0 ? (
        <p className="mt-4 text-sm text-brand-muted">No reports awaiting review.</p>
      ) : (
        <div className="mt-4 space-y-3">
          {labs.map((lab) => (
            <div
              key={lab.id}
              className="flex items-center justify-between gap-3 rounded-lg border border-brand-line/70 bg-slate-50/40 p-3 transition hover:bg-white hover:border-brand-primary/40"
              data-testid="pending-lab-card"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <p className="truncate text-xs font-bold text-brand-ink">
                    {lab.patientName} ({lab.patientMrn})
                  </p>
                  {lab.status === "NEW" ? (
                    <span className="inline-flex items-center gap-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-800">
                      <AlertCircle className="h-3 w-3" /> Pending Review
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 rounded bg-emerald-50 px-1.5 py-0.5 text-[10px] font-semibold text-emerald-700">
                      <CheckCircle className="h-3 w-3" /> Reviewed
                    </span>
                  )}
                </div>
                <p className="mt-0.5 truncate text-xs font-medium text-slate-600">{lab.title}</p>
                <p className="text-[11px] text-brand-muted">
                  Uploaded: {formatClinicDateTime(lab.createdAt)}
                </p>
              </div>

              <Link
                to={doctorPatientChartPath(lab.patientId)}
                className="inline-flex shrink-0 items-center gap-1 rounded-md border border-brand-line bg-white px-2.5 py-1 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary hover:text-white"
              >
                <Hourglass className="h-3 w-3" />
                Review
              </Link>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
