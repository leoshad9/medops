import { Navigate, Outlet } from "react-router-dom";

import { useAuth } from "../context/useAuth";
import { roleDashboardPath } from "../lib/roles";
import type { Role } from "../types/auth";

interface ProtectedRouteProps {
  allowedRoles: Role[];
}

export function ProtectedRoute({ allowedRoles }: Readonly<ProtectedRouteProps>) {
  const { user, isAuthenticated } = useAuth();

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />;
  }

  if (!allowedRoles.includes(user.role)) {
    return <Navigate to={roleDashboardPath(user.role)} replace />;
  }

  return <Outlet />;
}
