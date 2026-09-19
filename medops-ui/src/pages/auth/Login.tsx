import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { LoginBrandPanel } from "../../components/auth/LoginBrandPanel";
import { LoginForm } from "../../components/auth/LoginForm";
import { useAuth } from "../../context/useAuth";
import { messageFromApiError } from "../../lib/apiError";
import { roleDashboardPath } from "../../lib/roles";
import { SEO } from "../../components/seo/SEO";

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleLogin(email: string, password: string) {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      const user = await login(email, password);
      navigate(roleDashboardPath(user.role), { replace: true });
    } catch (error) {
      setErrorMessage(messageFromApiError(error, "Unable to sign in. Please try again."));
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <>
      <SEO
        title="Sign In"
        description="Access your MedOps account. Secure login for healthcare professionals to manage patient care, appointments, and clinical workflows."
        canonical="/login"
      />
      <div className="flex min-h-screen">
        <LoginBrandPanel className="hidden lg:block xl:block" />
        <div className="flex flex-1 items-center justify-center bg-gradient-to-br from-brand-primary-tint via-brand-paper to-brand-primary-tint/60 px-4 py-8 sm:px-6 sm:py-10 md:px-8 md:py-12 lg:px-10 lg:py-14 xl:px-12 xl:py-16 safe-top safe-bottom">
          <div className="w-full max-w-[480px]">
            <div className="lg:hidden text-center mb-8 px-4">
              <h1 className="fluid-text-2xl font-bold text-brand-primary-dark">MedOps</h1>
              <p className="fluid-text-sm text-brand-muted mt-1">Medical Operations Platform</p>
            </div>
            <LoginForm onSubmit={handleLogin} isLoading={isLoading} errorMessage={errorMessage} />
            <p className="mt-6 text-center fluid-text-sm text-brand-muted">
              Don&apos;t have an account?{" "}
              <Link to="/register" className="font-semibold text-brand-primary-dark hover:text-brand-primary focus-visible-ring">
                Register
              </Link>
            </p>
          </div>
        </div>
      </div>
    </>
  );
}