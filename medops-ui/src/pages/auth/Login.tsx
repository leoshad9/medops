import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { LoginBrandPanel } from "../../components/auth/LoginBrandPanel";
import { LoginForm } from "../../components/auth/LoginForm";
import { useAuth } from "../../context/useAuth";
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
      setErrorMessage(error instanceof Error ? error.message : "Something went wrong.");
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen">
      <LoginBrandPanel />
      <div className="flex flex-1 items-center justify-center bg-gradient-to-br from-blue-50 via-white to-blue-50 px-4 py-8 sm:px-6 sm:py-12">
        <div className="w-full max-w-md">
          <LoginForm onSubmit={handleLogin} isLoading={isLoading} errorMessage={errorMessage} />
          <p className="mt-6 text-center text-sm text-slate-500">
            Don&apos;t have an account?{" "}
            <Link to="/register" className="font-semibold text-blue-700 hover:text-blue-800">
              Register
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
