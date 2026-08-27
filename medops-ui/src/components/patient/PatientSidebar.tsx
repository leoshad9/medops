import { NavLink } from "react-router-dom";
import {
  Calendar,
  CalendarPlus,
  CreditCard,
  FlaskConical,
  Folder,
  Headset,
  HelpCircle,
  LayoutDashboard,
  LogOut,
  Pill,
  User,
  X,
} from "lucide-react";

import { useAuth } from "../../context/useAuth";
import { PATIENT_PATHS, type PatientViewKey } from "../../lib/patientRoutes";
import { MedOpsLogo } from "../icons/MedOpsLogo";

export type { PatientViewKey };

interface NavItem {
  id: PatientViewKey;
  label: string;
  icon: typeof LayoutDashboard;
}

const NAV_ITEMS: NavItem[] = [
  { id: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { id: "appointments", label: "Appointments", icon: Calendar },
  { id: "book", label: "Book Appointment", icon: CalendarPlus },
  { id: "prescriptions", label: "Prescriptions", icon: Pill },
  { id: "labs", label: "Lab Reports", icon: FlaskConical },
  { id: "records", label: "Medical Records", icon: Folder },
  { id: "billing", label: "Billing & Payments", icon: CreditCard },
  { id: "profile", label: "My Profile", icon: User },
  { id: "help", label: "Help & Support", icon: HelpCircle },
];

interface PatientSidebarProps {
  mobileOpen?: boolean;
  onCloseMobile?: () => void;
}

export function PatientSidebar({ mobileOpen = false, onCloseMobile }: Readonly<PatientSidebarProps>) {
  const { logout } = useAuth();

  return (
    <>
      {mobileOpen && (
        <button
          type="button"
          aria-label="Close menu overlay"
          onClick={onCloseMobile}
          className="fixed inset-0 z-40 bg-brand-ink/40 backdrop-blur-xs lg:hidden"
        />
      )}

      <aside
        className={`fixed top-0 bottom-0 left-0 z-50 flex w-64 shrink-0 flex-col border-r border-brand-line bg-white px-4 py-6 font-brand-sans transition-transform duration-200 lg:static lg:translate-x-0 ${
          mobileOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="flex items-center justify-between border-b border-brand-line px-2 pb-5">
          <div className="flex items-center gap-2.5">
            <MedOpsLogo className="h-7 w-7 text-brand-primary" />
            <div>
              <span className="text-lg font-bold tracking-tight text-brand-primary-dark">MEDOPS</span>
              <p className="text-[11px] font-medium text-brand-muted">Patient Portal</p>
            </div>
          </div>
          {onCloseMobile && (
            <button
              type="button"
              onClick={onCloseMobile}
              className="p-1 text-brand-muted hover:text-brand-ink lg:hidden"
              aria-label="Close menu"
            >
              <X className="h-5 w-5" />
            </button>
          )}
        </div>

        <nav className="mt-4 flex-1 space-y-1 overflow-y-auto">
          {NAV_ITEMS.map(({ id, label, icon: Icon }) => (
            <NavLink
              key={id}
              to={PATIENT_PATHS[id]}
              onClick={onCloseMobile}
              className={({ isActive }) =>
                `flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition cursor-pointer ${
                  isActive
                    ? "bg-brand-primary-tint text-brand-primary-dark font-semibold shadow-2xs"
                    : "text-slate-700 hover:bg-slate-50 hover:text-slate-900"
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon
                    className={`h-4 w-4 shrink-0 transition-opacity ${
                      isActive ? "opacity-100 text-brand-primary" : "opacity-75"
                    }`}
                  />
                  {label}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="mt-auto pt-3 border-t border-brand-line space-y-3">
          <div className="rounded-xl border border-brand-primary-tint bg-gradient-to-br from-brand-primary-tint via-[#f4faf9] to-white p-3.5">
            <p className="text-xs font-semibold text-brand-ink">Need help?</p>
            <p className="mt-1 text-[11px] leading-relaxed text-brand-muted">
              Our support team can help with your account, billing, or scheduling questions.
            </p>
            <NavLink
              to={PATIENT_PATHS.help}
              onClick={onCloseMobile}
              className="mt-2.5 flex w-full items-center justify-center gap-1.5 rounded-lg border border-brand-primary py-1.5 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary hover:text-white cursor-pointer bg-white"
            >
              <Headset className="h-3.5 w-3.5" />
              Contact Support
            </NavLink>
          </div>

          <button
            type="button"
            onClick={() => void logout()}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-red-50 hover:text-red-700 transition cursor-pointer"
          >
            <LogOut className="h-4 w-4 shrink-0" />
            Sign Out
          </button>
        </div>
      </aside>
    </>
  );
}
