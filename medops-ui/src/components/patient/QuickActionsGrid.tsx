import { Link } from "react-router-dom";
import { CalendarPlus, FlaskConical, Folder, Pill } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { PATIENT_PATHS, type PatientViewKey } from "../../lib/patientRoutes";

interface QuickAction {
  id: PatientViewKey;
  label: string;
  description: string;
  icon: LucideIcon;
}

const ACTIONS: QuickAction[] = [
  { id: "book", label: "Book Appointment", description: "Schedule a new visit", icon: CalendarPlus },
  { id: "prescriptions", label: "View Prescriptions", description: "Check active prescriptions", icon: Pill },
  { id: "labs", label: "View Lab Reports", description: "See your test results", icon: FlaskConical },
  { id: "records", label: "Medical Records", description: "Access your documents", icon: Folder },
];

const CHIP_STYLES = ["bg-brand-primary-tint text-brand-primary-dark", "bg-brand-amber-tint text-brand-amber"];

export function QuickActionsGrid() {
  return (
    <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
      <h2 className="font-bold text-brand-ink text-base">Quick Actions</h2>

      <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
        {ACTIONS.map(({ id, label, description, icon: Icon }, index) => (
          <Link
            key={label}
            to={PATIENT_PATHS[id]}
            className="flex flex-col items-start gap-2 rounded-xl border border-brand-line p-4 text-left transition hover:border-brand-primary hover:bg-brand-primary-tint/60 cursor-pointer group"
          >
            <span
              className={`flex h-8 w-8 items-center justify-center rounded-lg transition-transform group-hover:scale-105 ${CHIP_STYLES[index % CHIP_STYLES.length]}`}
            >
              <Icon className="h-4 w-4" />
            </span>
            <p className="text-sm font-semibold text-brand-ink">{label}</p>
            <p className="-mt-1 text-xs text-brand-muted">{description}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
