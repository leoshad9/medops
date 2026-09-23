import { Link } from "react-router-dom";
import { Calendar, FlaskConical, Pill, Search, Stethoscope } from "lucide-react";

import { recordAudit } from "../../lib/auditLog";
import { DOCTOR_PATHS } from "../../lib/doctorRoutes";

interface QuickAction {
  id: string;
  label: string;
  description: string;
  icon: typeof Search;
  path: string;
}

const ACTIONS: QuickAction[] = [
  { id: "find-patient", label: "Find Patient", description: "Search by name, MRN, phone", icon: Search, path: DOCTOR_PATHS.patients },
  { id: "new-consultation", label: "New Consultation", description: "Open a fresh chart", icon: Stethoscope, path: DOCTOR_PATHS.patients },
  { id: "prescription", label: "E-Prescription", description: "Write a new script", icon: Pill, path: DOCTOR_PATHS.prescriptions },
  { id: "order-lab", label: "Order Lab", description: "Upload a diagnostic report", icon: FlaskConical, path: DOCTOR_PATHS.labs },
  { id: "calendar", label: "Calendar", description: "View full schedule", icon: Calendar, path: DOCTOR_PATHS.appointments },
];

const ICON_BADGE =
  "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-primary-tint text-brand-primary-dark transition-transform group-hover:scale-105";

export function QuickActionsBar() {
  return (
    <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
      <h2 className="text-base font-bold text-brand-ink">Quick Actions</h2>
      <p className="mt-0.5 text-xs text-brand-muted">Fast access to your most-used clinical workflows</p>
      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {ACTIONS.map(({ id, label, description, icon: Icon, path }) => (
        <Link
          key={id}
          to={path}
          title={label}
          onClick={() => {
            if (id === "prescription") {
              recordAudit("PRESCRIPTION_TASK_INITIATED", { source: "quick_actions" });
            }
          }}
          className="group flex flex-col items-center gap-2 rounded-xl border border-brand-line p-3 text-center transition duration-150 hover:-translate-y-0.5 hover:border-brand-primary hover:bg-brand-primary-tint/60 focus-visible-ring"
        >
            <span className={ICON_BADGE}>
              <Icon className="h-5 w-5" />
            </span>
            <span className="block text-sm font-semibold text-brand-ink">{label}</span>
            <span className="block text-xs text-brand-muted">{description}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}