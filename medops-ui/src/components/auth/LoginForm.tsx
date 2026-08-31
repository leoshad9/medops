import { Eye, EyeOff, Loader2, Lock, Mail } from "lucide-react";
import { useState } from "react";
import type { SubmitEvent } from "react";

interface LoginFormProps {
  onSubmit: (email: string, password: string) => void;
  isLoading: boolean;
  errorMessage: string | null;
}

export function LoginForm({ onSubmit, isLoading, errorMessage }: Readonly<LoginFormProps>) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit(email, password);
  }

  return (
    <div className="w-full max-w-md rounded-2xl border border-brand-line bg-white p-6 shadow-2xl shadow-brand-ink/10 sm:p-10">
      <div className="flex flex-col items-center text-center">
        <h1 className="text-2xl font-bold text-brand-ink">Welcome</h1>
        <p className="mt-1 text-sm text-brand-muted">
          Sign in to your MedOps account
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
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="w-full rounded-lg border border-brand-line py-2.5 pr-3 pl-10 text-sm text-brand-ink outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
              placeholder="Enter your email"
            />
          </div>
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-semibold text-brand-ink">
            Password
          </label>
          <div className="relative mt-1.5">
            <Lock className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-brand-muted" />
            <input
              id="password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="w-full rounded-lg border border-brand-line py-2.5 pr-10 pl-10 text-sm text-brand-ink outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
              placeholder="Enter your password"
            />
            <button
              type="button"
              onClick={() => setShowPassword((value) => !value)}
              className="absolute top-1/2 right-3 -translate-y-1/2 text-brand-muted hover:text-brand-ink"
              aria-label={showPassword ? "Hide password" : "Show password"}
            >
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
        </div>

        <div className="flex justify-end">
          <button
            type="button"
            className="text-sm font-medium text-brand-primary-dark hover:text-brand-primary"
          >
            Forgot Password?
          </button>
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
            <Lock className="h-4 w-4" />
          )}
          {isLoading ? "Signing In..." : "Sign In"}
        </button>
      </form>
    </div>
  );
}
