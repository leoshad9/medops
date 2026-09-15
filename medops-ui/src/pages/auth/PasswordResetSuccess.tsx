import { Link } from "react-router-dom";

import { CheckCircle } from "lucide-react";

export function PasswordResetSuccess() {
  return (
    <div className="flex min-h-screen">
      <div className="flex flex-1 items-center justify-center bg-gradient-to-br from-brand-primary-tint via-brand-paper to-brand-primary-tint/60 px-4 py-8 sm:px-6 sm:py-12">
        <div className="w-full max-w-md">
          <div className="w-full max-w-md rounded-2xl border border-brand-line bg-white p-6 shadow-2xl shadow-brand-ink/10 sm:p-10 text-center">
            <div className="flex flex-col items-center">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-success/10">
                <CheckCircle className="h-8 w-8 text-brand-success" />
              </div>
              <h1 className="mt-6 text-2xl font-bold text-brand-ink">Password Reset Successfully</h1>
              <p className="mt-2 text-sm text-brand-muted">
                Your password has been updated. You can now log in with your new password.
              </p>
            </div>

            <div className="mt-8">
              <Link
                to="/login"
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand-primary py-2.5 text-sm font-semibold text-white transition hover:bg-brand-primary-dark"
              >
                Back to Login
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
