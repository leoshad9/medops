import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { LoginBrandPanel } from "../../components/auth/LoginBrandPanel";
import { RegisterDoctorForm } from "../../components/auth/RegisterDoctorForm";
import { RegisterPatientForm } from "../../components/auth/RegisterPatientForm";
import { useAuth } from "../../context/useAuth";
import { messageFromApiError } from "../../lib/apiError";
import { roleDashboardPath } from "../../lib/roles";
import type { Role } from "../../types/auth";

export function Register() {
  const { registerPatient, registerDoctor } = useAuth();
  const navigate = useNavigate();
  const [role, setRole] = useState<Role>("PATIENT");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleRegister(register: () => Promise<{ role: Role }>) {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const user = await register();
      navigate(roleDashboardPath(user.role), { replace: true });
    } catch (error) {
      setErrorMessage(messageFromApiError(error, "Unable to create your account. Please try again."));
    } finally {
      setIsLoading(false);
    }
  }

  function selectRole(nextRole: Role) {
    setRole(nextRole);
    setErrorMessage(null);
  }

  return (
    <div className="flex min-h-screen">
      <LoginBrandPanel />
      <div className="flex flex-1 items-center justify-center bg-gradient-to-br from-brand-primary-tint via-brand-paper to-brand-primary-tint/60 px-4 py-8 sm:px-6 sm:py-12">
        <div className="w-full max-w-md rounded-2xl border border-brand-line bg-white p-6 shadow-2xl shadow-brand-ink/10 sm:p-10">
          <div className="flex flex-col items-center text-center">
            <h1 className="text-2xl font-bold text-brand-ink">Create Account</h1>
            <p className="mt-1 text-sm text-brand-muted">Join MedOps to get started</p>
          </div>

          <div className="mt-6 grid grid-cols-2 gap-2 rounded-lg bg-brand-primary-tint p-1">
            <button
              type="button"
              onClick={() => selectRole("PATIENT")}
              className={`rounded-md py-2 text-sm font-semibold transition ${
                role === "PATIENT" ? "bg-white text-brand-primary-dark shadow" : "text-brand-muted hover:text-brand-ink"
              }`}
            >
              I&apos;m a Patient
            </button>
            <button
              type="button"
              onClick={() => selectRole("DOCTOR")}
              className={`rounded-md py-2 text-sm font-semibold transition ${
                role === "DOCTOR" ? "bg-white text-brand-primary-dark shadow" : "text-brand-muted hover:text-brand-ink"
              }`}
            >
              I&apos;m a Doctor
            </button>
          </div>

          {role === "PATIENT" ? (
            <RegisterPatientForm
              isLoading={isLoading}
              errorMessage={errorMessage}
              onSubmit={(request) => handleRegister(() => registerPatient(request))}
            />
          ) : (
            <RegisterDoctorForm
              isLoading={isLoading}
              errorMessage={errorMessage}
              onSubmit={(request) => handleRegister(() => registerDoctor(request))}
            />
          )}

          <p className="mt-6 text-center text-sm text-brand-muted">
            Already have an account?{" "}
            <Link to="/login" className="font-semibold text-brand-primary-dark hover:text-brand-primary">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
