import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { Loader2, Mail } from "lucide-react";
import { forgotPassword } from "../../services/authService";
import { messageFromApiError } from "../../lib/apiError";

export function ForgotPassword() {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsLoading(true);
    setErrorMessage(null);

    const formData = new FormData(event.currentTarget);
    const email = formData.get("email") as string;

    try {
      const flowId = await forgotPassword(email);
      navigate(`/verify-otp?flowId=${flowId}`);
    } catch (error) {
      setErrorMessage(messageFromApiError(error, "Unable to send OTP. Please try again."));
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen">
      <div className="flex flex-1 items-center justify-center bg-gradient-to-br from-brand-primary-tint via-brand-paper to-brand-primary-tint/60 px-4 py-8 sm:px-6 sm:py-12">
        <div className="w-full max-w-md">
          <div className="w-full max-w-md rounded-2xl border border-brand-line bg-white p-6 shadow-2xl shadow-brand-ink/10 sm:p-10">
            <div className="flex flex-col items-center text-center">
              <h1 className="text-2xl font-bold text-brand-ink">Forgot Password</h1>
              <p className="mt-1 text-sm text-brand-muted">
                Enter your email and we'll send you a 6-digit OTP to reset your password
              </p>
            </div>

            <form onSubmit={handleSubmit} className="mt-8 space-y-5">
              <div>
                <label htmlFor="email" className="block text-sm font-semibold text-brand-ink">
                  Email Address
                </label>
                <div className="relative mt-1.5">
                  <Mail className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-brand-muted" />
                  <input
                    id="email"
                    name="email"
                    type="email"
                    autoComplete="email"
                    required
                    className="w-full rounded-lg border border-brand-line py-2.5 pr-3 pl-10 text-sm text-brand-ink outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
                    placeholder="Enter your email"
                  />
                </div>
              </div>

              {errorMessage && (
                <p className="rounded-lg bg-brand-rust-tint px-3 py-2 text-sm text-brand-rust">
                  {errorMessage}
                </p>
              )}


              <button
                type="submit"
                disabled={isLoading}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand-primary py-2.5 text-sm font-semibold text-white transition hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-70"
              >
                {isLoading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Mail className="h-4 w-4" />
                )}
                {isLoading ? "Sending..." : "Send OTP"}
              </button>
            </form>

            <p className="mt-6 text-center text-sm text-brand-muted">
              Remember your password?{" "}
              <Link to="/login" className="font-semibold text-brand-primary-dark hover:text-brand-primary">
                Back to Login
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
