import { Link } from "react-router-dom";
import { ArrowUpRight, Pill, RefreshCw, Send, Stethoscope, XCircle } from "lucide-react";

import type { PrescriptionTask } from "../../types/doctor";

const BUCKET_STYLES: Record<PrescriptionTask["bucket"], { label: string; badge: string; icon: typeof Pill }> = {
  DRAFT: { label: "Draft", badge: "bg-slate-100 text-slate-700", icon: Pill },
  PENDING_SIGNATURE: { label: "Pending Signature", badge: "bg-amber-100 text-amber-800", icon: Send },
  RENEWAL: { label: "Renewal", badge: "bg-brand-primary-tint text-brand-primary-dark", icon: RefreshCw },
  FAILED_TRANSMISSION: { label: "Failed", badge: "bg-brand-rust-tint text-brand-rust", icon: XCircle },
  RECENTLY_ISSUED: { label: "Issued", badge: "bg-emerald-50 text-emerald-700", icon: Pill },
};

interface PrescriptionTasksProps {
  tasks: PrescriptionTask[];
}

export function PrescriptionTasks({ tasks }: Readonly<PrescriptionTasksProps>) {
  if (tasks.length === 0) {
    return (
      <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
        <div className="flex items-center justify-between border-b border-brand-line pb-3">
          <div className="flex items-center gap-2">
            <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-brand-primary-tint text-brand-primary-dark">
              <Pill className="h-4 w-4" />
            </span>
            <h2 className="text-sm font-bold text-brand-ink">Prescription Tasks</h2>
          </div>
        </div>
        <p className="mt-4 text-sm text-brand-muted">No prescription tasks pending.</p>
        <Link
          to="/doctor/prescriptions"
          className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
        >
          Create e-prescription
          <ArrowUpRight className="h-3.5 w-3.5" />
        </Link>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
      <div className="flex items-center justify-between border-b border-brand-line pb-3">
        <div className="flex items-center gap-2">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-brand-primary-tint text-brand-primary-dark">
            <Pill className="h-4 w-4" />
          </span>
          <h2 className="text-sm font-bold text-brand-ink">Prescription Tasks</h2>
        </div>
        <Link
          to="/doctor/prescriptions"
          className="inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
        >
          All Prescriptions
          <ArrowUpRight className="h-3 w-3" />
        </Link>
      </div>
      <div className="mt-4 space-y-3">
        {tasks.map((task) => {
          const style = BUCKET_STYLES[task.bucket];
          const Icon = style.icon;
          return (
            <div
              key={task.id}
              className="flex items-start justify-between gap-3 rounded-lg border border-brand-line/70 bg-slate-50/40 p-3 transition hover:bg-white hover:border-brand-primary/40"
            >
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-brand-ink">
                  {task.medicationName}
                </p>
                <p className="font-mono text-xs text-brand-muted">
                  {task.patientName} · {task.patientMrn}
                </p>
                <span
                  className={`mt-1 inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-semibold ${style.badge}`}
                >
                  <Icon className="h-3 w-3" />
                  {style.label}
                </span>
              </div>
              <Link
                to={task.chartPath}
                title="Open patient chart"
                className="inline-flex shrink-0 items-center gap-1 rounded-md border border-brand-line bg-white px-2.5 py-1 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary hover:text-white"
              >
                <Stethoscope className="h-3 w-3" />
                Chart
              </Link>
            </div>
          );
        })}
      </div>
    </div>
  );
}