import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";

import { Loader2, Mail } from "lucide-react";
import { forgotPassword } from "../../services/authService";
import { messageFromApiError } from "../../lib/apiError";
import { SEO } from "../../components/seo/SEO";

/** Renders the page that starts a password-recovery flow. */
export function ForgotPassword() {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  /** Submits the recovery email and routes to OTP verification when appropriate. */
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsLoading(true);
    setErrorMessage(null);

    const formData = new FormData(event.currentTarget);
    const email = formData.get("email") as string;

    try {
      const { resetFlowId: flowId, message } = await forgotPassword(email);
      if (flowId == null) {
        setErrorMessage(message);
        return;
      }
      navigate(`/verify-otp?flowId=${flowId}`);
    } catch (error) {
      setErrorMessage(messageFromApiError(error, "Unable to send OTP. Please try again."));
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <>
      <SEO
        title="Forgot Password"
        description="Reset your MedOps password. Enter your email to receive a 6-digit OTP for secure password recovery."
        canonical="/forgot-password"
        noIndex={true}
        noFollow={true}
      />
      <div className="flex min-h-screen">
        <div className="flex flex-1 items-center justify-center bg-gradient-to-br from-brand-primary-tint via-brand-paper to-brand-primary-tint/60 px-4 py-8 sm:px-6 sm:py-10 md:px-8 md:py-12 lg:px-10 lg:py-14 xl:px-12 xl:py-16 safe-top safe-bottom">
          <div className="w-full max-md rounded-2xl border border-brand-line bg-white p-6 shadow-2xl shadow-brand-ink/10 sm:p-8 lg:p-10 xl:p-12">
            <div className="flex flex-col items-center text-center">
              <h1 className="fluid-text-xl lg:fluid-text-2xl font-bold text-brand-ink">Forgot Password</h1>
              <p className="mt-1 fluid-text-sm text-brand-muted">
                Enter your email and we&apos;ll send you a 6-digit OTP to reset your password
              </p>
            </div>

            <form onSubmit={handleSubmit} className="mt-6 lg:mt-8 space-y-5">
              <div>
                <label htmlFor="email" className="block fluid-text-sm font-semibold text-brand-ink">
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
                    className="w-full rounded-lg border border-brand-line py-2.5 pr-3 pl-10 fluid-text-sm text-brand-ink outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary touch-target"
                    placeholder="Enter your email"
                  />
                </div>
              </div>

              {errorMessage && (
                <p className="rounded-lg bg-brand-rust-tint px-3 py-2 fluid-text-sm text-brand-rust">
                  {errorMessage}
                </p>
              )}

              <button
                type="submit"
                disabled={isLoading}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand-primary py-2.5 fluid-text-sm font-semibold text-white transition hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-70 touch-target"
              >
                {isLoading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Mail className="h-4 w-4" />
                )}
                {isLoading ? "Sending..." : "Send OTP"}
              </button>
            </form>

            <p className="mt-6 text-center fluid-text-sm text-brand-muted">
              Remember your password?{" "}
              <Link to="/login" className="font-semibold text-brand-primary-dark hover:text-brand-primary focus-visible-ring">
                Back to Login
              </Link>
            </p>
          </div>
        </div>
      </div>
    </>
  );
}