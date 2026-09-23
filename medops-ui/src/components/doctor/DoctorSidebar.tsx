import { NavLink } from "react-router-dom";
import {
  Calendar,
  FlaskConical,
  LayoutDashboard,
  Bell,
  LogOut,
  Pill,
  User,
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
  { id: "notifications", label: "Notifications", icon: Bell },
  { id: "profile", label: "My Profile", icon: User },
];

export function DoctorSidebar() {
  const { logout } = useAuth();

  return (
    <aside className="flex h-dvh w-64 shrink-0 flex-col overflow-hidden border-r border-brand-line bg-white px-4 py-6 font-brand-sans lg:w-72 lg:sticky lg:top-0">
      <div className="flex items-center gap-2 border-b border-brand-line px-2 pb-6">
        <MedOpsLogo className="h-10 w-10 text-brand-primary" />
        <div>
          <span className="text-lg font-bold text-brand-primary-dark">MEDOPS</span>
          <p className="text-xs text-brand-muted">Doctor Clinical Workspace</p>
        </div>
      </div>

      <nav className="mt-4 min-h-0 flex-1 space-y-0.5 overflow-y-auto scrollbar-thin">
        {NAV_ITEMS.map(({ id, label, icon: Icon }) => (
          <NavLink
            key={label}
            to={DOCTOR_PATHS[id]}
            end={id === "dashboard"}
            className={({ isActive }) =>
              `flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition touch-target ${
                isActive
                  ? "bg-brand-primary-tint text-brand-primary-dark"
                  : "text-slate-700 hover:bg-slate-50 hover:text-slate-900"
              }`
            }
          >
            <Icon className="h-4 w-4 shrink-0" />
            <span className="truncate">{label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="mt-auto shrink-0 border-t border-brand-line pt-4">
        <button
          type="button"
          onClick={() => void logout()}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-slate-600 hover:bg-red-50 hover:text-red-700 transition touch-target"
        >
          <LogOut className="h-4 w-4 shrink-0" />
          <span className="truncate">Sign Out</span>
        </button>
      </div>
    </aside>
  );
}
