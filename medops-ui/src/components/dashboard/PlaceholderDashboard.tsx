import { useAuth } from "../../context/useAuth";
import type { Role } from "../../types/auth";

interface PlaceholderDashboardProps {
  role: Role;
}

export function PlaceholderDashboard({ role }: Readonly<PlaceholderDashboardProps>) {
  const { user, logout } = useAuth();

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-50 px-6 text-center">
      <p className="text-sm font-semibold tracking-wide text-blue-600 uppercase">{role}</p>
      <h1 className="text-2xl font-bold text-slate-900">Welcome, {user?.email}</h1>
      <p className="max-w-sm text-sm text-slate-500">
        This is a placeholder dashboard — real content lands once the backend
        has data for this role.
      </p>
      <button
        type="button"
        onClick={() => void logout()}
        className="mt-4 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-800"
      >
        Log out
      </button>
    </div>
  );
}
