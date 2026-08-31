import { NavLink } from "react-router-dom";
import {
  Calendar,
  FlaskConical,
  Headset,
  LayoutDashboard,
  LogOut,
  Pill,
  Users,
} from "lucide-react";

import { useAuth } from "../../context/useAuth";
import { DOCTOR_PATHS, type DoctorViewKey } from "../../lib/doctorRoutes";
import { MedOpsLogo } from "../icons/MedOpsLogo";

const NAV_ITEMS: { id: DoctorViewKey; label: string; icon: typeof LayoutDashboard }[] = [
  { id: "dashboard", label: "Clinical Overview", icon: LayoutDashboard },
  { id: "appointments", label: "Appointments Schedule", icon: Calendar },
  { id: "patients", label: "Patient Roster", icon: Users },
  { id: "prescriptions", label: "E-Prescriptions", icon: Pill },
  { id: "labs", label: "Diagnostic & Labs", icon: FlaskConical },
];

export function DoctorSidebar() {
  const { logout } = useAuth();

  return (
    <aside className="flex w-64 shrink-0 flex-col border-r border-brand-line bg-white px-4 py-6 font-brand-sans">
      <div className="flex items-center gap-2 border-b border-brand-line px-2 pb-6">
        <MedOpsLogo className="h-8 w-8 text-brand-primary" />
        <div>
          <span className="text-lg font-bold text-brand-primary-dark">MEDOPS</span>
          <p className="text-xs text-brand-muted">Doctor Clinical Workspace</p>
        </div>
      </div>

      <nav className="mt-4 flex-1 space-y-0.5">
        {NAV_ITEMS.map(({ id, label, icon: Icon }) => (
          <NavLink
            key={label}
            to={DOCTOR_PATHS[id]}
            end={id === "dashboard"}
            className={({ isActive }) =>
              `flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
                isActive
                  ? "bg-brand-primary-tint text-brand-primary-dark"
                  : "text-slate-700 hover:bg-slate-50 hover:text-slate-900"
              }`
            }
          >
            <Icon className="h-4 w-4 shrink-0" />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="mt-auto pt-4 border-t border-brand-line space-y-3">
        <div className="rounded-xl border border-brand-primary-tint bg-gradient-to-br from-brand-primary-tint to-white p-4">
          <p className="text-sm font-semibold text-brand-ink">On-Call Support</p>
          <p className="mt-1 text-xs leading-relaxed text-brand-muted">
            Need immediate hospital IT, pharmacy liaison, or on-call staff coordination?
          </p>
          <span className="mt-3 flex w-full items-center justify-center gap-1.5 rounded-lg border border-brand-primary py-2 text-xs font-semibold text-brand-primary-dark">
            <Headset className="h-3.5 w-3.5" />
            Page Clinical Ops
          </span>
        </div>
        <button
          type="button"
          onClick={() => void logout()}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-red-50 hover:text-red-700 transition"
        >
          <LogOut className="h-4 w-4 shrink-0" />
          Sign Out
        </button>
      </div>
    </aside>
  );
}
