import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { LoginBrandPanel } from "../../components/auth/LoginBrandPanel";
import { RegisterDoctorForm } from "../../components/auth/RegisterDoctorForm";
import { RegisterPatientForm } from "../../components/auth/RegisterPatientForm";
import { useAuth } from "../../context/useAuth";
import { messageFromApiError } from "../../lib/apiError";
import { roleDashboardPath } from "../../lib/roles";
import type { Role } from "../../types/auth";
import { SEO } from "../../components/seo/SEO";

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
    <>
      <SEO
        title="Create Account"
        description="Join MedOps - the AI-powered medical operations platform. Register as a patient or doctor to access secure healthcare management tools."
        canonical="/register"
      />
      <div className="flex min-h-screen">
        <LoginBrandPanel className="hidden lg:block xl:block" />
        <div className="flex flex-1 items-center justify-center bg-gradient-to-br from-brand-primary-tint via-brand-paper to-brand-primary-tint/60 px-4 py-8 sm:px-6 sm:py-10 md:px-8 md:py-12 lg:px-10 lg:py-14 xl:px-12 xl:py-16 safe-top safe-bottom">
          <div className="w-full max-md rounded-2xl border border-brand-line bg-white p-6 shadow-2xl shadow-brand-ink/10 sm:p-8 lg:p-10 xl:p-12">
            <div className="lg:hidden text-center mb-6">
              <h1 className="fluid-text-2xl font-bold text-brand-primary-dark">MedOps</h1>
              <p className="fluid-text-sm text-brand-muted mt-1">Medical Operations Platform</p>
            </div>
            <div className="flex flex-col items-center text-center mb-6 lg:mb-8">
              <h1 className="fluid-text-xl lg:fluid-text-2xl font-bold text-brand-ink">Create Account</h1>
              <p className="mt-1 fluid-text-sm text-brand-muted">Join MedOps to get started</p>
            </div>

            <div className="mt-4 lg:mt-6 grid grid-cols-2 gap-2 rounded-lg bg-brand-primary-tint p-1">
              <button
                type="button"
                onClick={() => selectRole("PATIENT")}
                className={`rounded-md py-2.5 text-sm font-semibold transition touch-target ${
                  role === "PATIENT" ? "bg-white text-brand-primary-dark shadow" : "text-brand-muted hover:text-brand-ink"
                }`}
              >
                I&apos;m a Patient
              </button>
              <button
                type="button"
                onClick={() => selectRole("DOCTOR")}
                className={`rounded-md py-2.5 text-sm font-semibold transition touch-target ${
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

            <p className="mt-6 text-center fluid-text-sm text-brand-muted">
              Already have an account?{" "}
              <Link to="/login" className="font-semibold text-brand-primary-dark hover:text-brand-primary focus-visible-ring">
                Sign in
              </Link>
            </p>
          </div>
        </div>
      </div>
    </>
  );
}