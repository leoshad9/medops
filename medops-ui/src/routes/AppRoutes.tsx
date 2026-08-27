import { Navigate, Route, Routes } from "react-router-dom";

import { PatientLayout } from "../components/patient/PatientLayout";
import { useAuth } from "../context/useAuth";
import { roleDashboardPath } from "../lib/roles";
import { Login } from "../pages/auth/Login";
import { Register } from "../pages/auth/Register";
import { DoctorDashboard } from "../pages/doctor/Dashboard";
import { PatientDashboard } from "../pages/patient/Dashboard";
import { AppointmentsView } from "../pages/patient/views/AppointmentsView";
import { BillingView } from "../pages/patient/views/BillingView";
import { BookAppointmentView } from "../pages/patient/views/BookAppointmentView";
import { HelpSupportView } from "../pages/patient/views/HelpSupportView";
import { LabReportsView } from "../pages/patient/views/LabReportsView";
import { MedicalRecordsView } from "../pages/patient/views/MedicalRecordsView";
import { PrescriptionsView } from "../pages/patient/views/PrescriptionsView";
import { ProfileView } from "../pages/patient/views/ProfileView";
import { ProtectedRoute } from "./ProtectedRoute";

export function AppRoutes() {
  const { user, isAuthenticated } = useAuth();
  const homePath = isAuthenticated && user ? roleDashboardPath(user.role) : "/login";

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated && user ? <Navigate to={roleDashboardPath(user.role)} replace /> : <Login />}
      />
      <Route
        path="/register"
        element={isAuthenticated && user ? <Navigate to={roleDashboardPath(user.role)} replace /> : <Register />}
      />

      <Route element={<ProtectedRoute allowedRoles={["DOCTOR"]} />}>
        <Route path="/doctor" element={<Navigate to="/doctor/dashboard" replace />} />
        <Route path="/doctor/dashboard" element={<DoctorDashboard />} />
      </Route>
      <Route element={<ProtectedRoute allowedRoles={["PATIENT"]} />}>
        <Route path="/patient" element={<PatientLayout />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<PatientDashboard />} />
          <Route path="appointments" element={<AppointmentsView />} />
          <Route path="book" element={<BookAppointmentView />} />
          <Route path="prescriptions" element={<PrescriptionsView />} />
          <Route path="labs" element={<LabReportsView />} />
          <Route path="records" element={<MedicalRecordsView />} />
          <Route path="billing" element={<BillingView />} />
          <Route path="profile" element={<ProfileView />} />
          <Route path="help" element={<HelpSupportView />} />
        </Route>
      </Route>

      <Route path="/" element={<Navigate to={homePath} replace />} />
      <Route path="*" element={<Navigate to={homePath} replace />} />
    </Routes>
  );
}
