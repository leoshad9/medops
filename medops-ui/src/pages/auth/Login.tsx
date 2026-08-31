import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { LoginBrandPanel } from "../../components/auth/LoginBrandPanel";
import { LoginForm } from "../../components/auth/LoginForm";
import { useAuth } from "../../context/useAuth";
import { messageFromApiError } from "../../lib/apiError";
import { roleDashboardPath } from "../../lib/roles";

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
    <div className="flex min-h-screen">
      <LoginBrandPanel />
      <div className="flex flex-1 items-center justify-center bg-gradient-to-br from-brand-primary-tint via-brand-paper to-brand-primary-tint/60 px-4 py-8 sm:px-6 sm:py-12">
        <div className="w-full max-w-md">
          <LoginForm onSubmit={handleLogin} isLoading={isLoading} errorMessage={errorMessage} />
          <p className="mt-6 text-center text-sm text-brand-muted">
            Don&apos;t have an account?{" "}
            <Link to="/register" className="font-semibold text-brand-primary-dark hover:text-brand-primary">
              Register
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
