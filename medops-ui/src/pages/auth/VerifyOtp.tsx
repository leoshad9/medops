import { useState, useEffect, useCallback, useRef } from "react";
import type { FormEvent, MouseEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";

import { Loader2, RotateCcw } from "lucide-react";
import { resendOtp, verifyOtp } from "../../services/authService";
import { messageFromApiError } from "../../lib/apiError";
import { LoginBrandPanel } from "../../components/auth/LoginBrandPanel";

/** Renders OTP verification and resend controls for a recovery flow. */
export function VerifyOtp() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const flowId = searchParams.get("flowId");
  const [otpSlots, setOtpSlots] = useState(["", "", "", "", "", ""]);
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const isOtpComplete = otpSlots.every((d) => d !== "");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [resendCooldown, setResendCooldown] = useState(0);
  const cooldownTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const startCooldown = useCallback(() => {
    if (cooldownTimerRef.current) {
      clearInterval(cooldownTimerRef.current);
    }
    setResendCooldown(60);
    cooldownTimerRef.current = setInterval(() => {
      setResendCooldown((prev) => {
        if (prev <= 1) {
          if (cooldownTimerRef.current) {
            clearInterval(cooldownTimerRef.current);
            cooldownTimerRef.current = null;
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  }, []);

  useEffect(() => {
    return () => {
      if (cooldownTimerRef.current) {
        clearInterval(cooldownTimerRef.current);
        cooldownTimerRef.current = null;
      }
    };
  }, []);

  /** Submits a complete OTP and advances to password reset. */
  async function handleVerify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!flowId) {
      setErrorMessage("Invalid reset flow. Please start over.");
      return;
    }
    if (!isOtpComplete) {
      setErrorMessage("Please enter a 6-digit OTP.");
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      await verifyOtp(flowId, otpSlots.join(""));
      navigate("/reset-password");
    } catch (error) {
      setErrorMessage(messageFromApiError(error, "Invalid OTP. Please try again."));
    } finally {
      setIsLoading(false);
    }
  }

  /** Requests a replacement OTP and restarts the resend cooldown. */
  async function handleResend(event: MouseEvent<HTMLButtonElement>) {
    event.preventDefault();
    if (!flowId || resendCooldown > 0) return;

    setIsResending(true);
    setErrorMessage(null);

    try {
      const response = await resendOtp(flowId);
      if (response.status === "SENT") {
        startCooldown();
      } else {
        setErrorMessage(response.message);
      }
    } catch (error) {
      setErrorMessage(messageFromApiError(error, "Unable to resend OTP. Please try again."));
    } finally {
      setIsResending(false);
    }
  }

  return (
    <div className="flex min-h-screen">
      <LoginBrandPanel className="hidden lg:block xl:block" />
      <div className="flex flex-1 items-center justify-center bg-gradient-to-br from-brand-primary-tint via-brand-paper to-brand-primary-tint/60 px-4 py-8 sm:px-6 sm:py-10 md:px-8 md:py-12 lg:px-10 lg:py-14 xl:px-12 xl:py-16 safe-top safe-bottom">
        <div className="w-full max-w-[480px]">
          <div className="lg:hidden text-center mb-8 px-4">
            <h1 className="fluid-text-2xl font-bold text-brand-primary-dark">MedOps</h1>
            <p className="fluid-text-sm text-brand-muted mt-1">Medical Operations Platform</p>
          </div>
          <div className="w-full max-w-[480px] rounded-2xl border border-brand-line bg-white p-6 shadow-2xl shadow-brand-ink/10 sm:p-10">
            <div className="flex flex-col items-center text-center">
              <h1 className="text-2xl font-bold text-brand-ink">Verify OTP</h1>
              <p className="mt-1 text-sm text-brand-muted">
                Enter the 6-digit code sent to your email
              </p>
            </div>

            <form onSubmit={handleVerify} className="mt-8 space-y-5">
              <div className="flex justify-center gap-3">
                {Array.from({ length: 6 }, (_, i) => (
                  <input
                    key={i}
                    type="text"
                     maxLength={1}
                     value={otpSlots[i]}
                     onChange={(e) => {
                       const value = e.currentTarget.value;
                       if (/^\d*$/.test(value) && value.length <= 1) {
                         setOtpSlots((prev) => {
                           const next = [...prev];
                           next[i] = value;
                           return next;
                         });
                       if (value && i < 5) {
                         const nextInput = e.currentTarget.parentElement?.querySelector(`input[data-index="${i + 1}"]`) as HTMLInputElement;
                         nextInput?.focus();
                       }
                     }
                     }}
                     onKeyDown={(e) => {
                      if (e.key === "Backspace" && !otpSlots[i] && i > 0) {
                        const prevInput = e.currentTarget.parentElement?.querySelector(`input[data-index="${i - 1}"]`) as HTMLInputElement;
                        prevInput?.focus();
                      }
                    }}
                    data-index={i}
                    className="w-10 h-12 text-center text-2xl font-bold rounded-lg border border-brand-line focus:border-brand-primary focus:ring-1 focus:ring-brand-primary outline-none"
                    autoComplete="one-time-code"
                    inputMode="numeric"
                  />
                ))}
              </div>

              {errorMessage && (
                <p className="rounded-lg bg-brand-rust-tint px-3 py-2 text-sm text-brand-rust text-center">
                  {errorMessage}
                </p>
              )}

              <button
                type="submit"
                disabled={isLoading || !isOtpComplete}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand-primary py-2.5 text-sm font-semibold text-white transition hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-70"
              >
                {isLoading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <>
                    <RotateCcw className="h-4 w-4" />
                    Verify OTP
                  </>
                )}
              </button>
            </form>

            <div className="mt-6 text-center">
              <button
                onClick={handleResend}
                disabled={resendCooldown > 0 || isResending}
                className="text-sm font-medium text-brand-primary-dark hover:text-brand-primary disabled:text-brand-muted disabled:cursor-not-allowed"
              >
                {resendCooldown > 0 ? (
                  <>
                    Resend OTP in {resendCooldown}s
                    <RotateCcw className="inline-block h-4 w-4 animate-spin ml-1" />
                  </>
                ) : (
                  "Resend OTP"
                )}
              </button>
            </div>

            <p className="mt-6 text-center text-sm text-brand-muted">
              <Link to="/forgot-password" className="font-semibold text-brand-primary-dark hover:text-brand-primary">
                Back to Forgot Password
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
