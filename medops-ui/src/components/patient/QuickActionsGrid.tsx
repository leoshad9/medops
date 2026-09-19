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

// Shared teal badge style, matching the overview stat cards above.
const ICON_BADGE =
  "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-primary-tint text-brand-primary-dark transition-transform group-hover:scale-105";

export function QuickActionsGrid() {
  return (
    <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
      <h2 className="font-bold text-brand-ink text-base">Quick Actions</h2>

      {/* Compact 2x2 tile grid so the card stays level with Upcoming Appointment. */}
      <div className="mt-4 grid grid-cols-2 gap-3">
        {ACTIONS.map(({ id, label, description, icon: Icon }) => (
          <Link
            key={label}
            to={PATIENT_PATHS[id]}
            title={label}
            className="group flex items-center gap-3 rounded-xl border border-brand-line p-3 text-left transition duration-150 hover:-translate-y-0.5 hover:border-brand-primary hover:bg-brand-primary-tint/60 hover:shadow-sm cursor-pointer focus-visible-ring"
          >
            <span className={ICON_BADGE}>
              <Icon className="h-5 w-5" />
            </span>
            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold text-brand-ink">{label}</span>
              <span className="block truncate text-xs text-brand-muted">{description}</span>
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}
