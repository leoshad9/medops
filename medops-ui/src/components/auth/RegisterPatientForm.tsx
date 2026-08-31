import { Calendar, Eye, EyeOff, Loader2, Lock, Mail, Phone, User, UserRound } from "lucide-react";
import { useState } from "react";
import type { SubmitEvent } from "react";

import type { Gender, RegisterPatientRequest } from "../../types/auth";

interface RegisterPatientFormProps {
  onSubmit: (request: RegisterPatientRequest) => void;
  isLoading: boolean;
  errorMessage: string | null;
}

export function RegisterPatientForm({ onSubmit, isLoading, errorMessage }: Readonly<RegisterPatientFormProps>) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [fullName, setFullName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [gender, setGender] = useState<Gender>("FEMALE");
  const [phoneNumber, setPhoneNumber] = useState("");

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({ email, password, fullName, dateOfBirth, gender, phoneNumber });
  }

  return (
    <form onSubmit={handleSubmit} className="mt-8 space-y-5">
      <div>
        <label htmlFor="patient-full-name" className="block text-sm font-semibold text-slate-800">
          Full Name
        </label>
        <div className="relative mt-1.5">
          <User className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            id="patient-full-name"
            type="text"
            autoComplete="name"
            required
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="Enter your full name"
          />
        </div>
      </div>

      <div>
        <label htmlFor="patient-email" className="block text-sm font-semibold text-slate-800">
          Email Address
        </label>
        <div className="relative mt-1.5">
          <Mail className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            id="patient-email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="Enter your email"
          />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor="patient-dob" className="block text-sm font-semibold text-slate-800">
            Date of Birth
          </label>
          <div className="relative mt-1.5">
            <Calendar className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              id="patient-dob"
              type="date"
              required
              value={dateOfBirth}
              onChange={(event) => setDateOfBirth(event.target.value)}
              className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            />
          </div>
        </div>

        <div>
          <label htmlFor="patient-gender" className="block text-sm font-semibold text-slate-800">
            Gender
          </label>
          <div className="relative mt-1.5">
            <UserRound className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <select
              id="patient-gender"
              required
              value={gender}
              onChange={(event) => setGender(event.target.value as Gender)}
              className="w-full appearance-none rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            >
              <option value="FEMALE">Female</option>
              <option value="MALE">Male</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
        </div>
      </div>

      <div>
        <label htmlFor="patient-phone" className="block text-sm font-semibold text-slate-800">
          Phone Number
        </label>
        <div className="relative mt-1.5">
          <Phone className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            id="patient-phone"
            type="tel"
            autoComplete="tel"
            required
            value={phoneNumber}
            onChange={(event) => setPhoneNumber(event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="+1 234 567 8901"
          />
        </div>
      </div>

      <div>
        <label htmlFor="patient-password" className="block text-sm font-semibold text-slate-800">
          Password
        </label>
        <div className="relative mt-1.5">
          <Lock className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            id="patient-password"
            type={showPassword ? "text" : "password"}
            autoComplete="new-password"
            required
            minLength={8}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-10 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="At least 8 characters"
          />
          <button
            type="button"
            onClick={() => setShowPassword((value) => !value)}
            className="absolute top-1/2 right-3 -translate-y-1/2 text-slate-400 hover:text-slate-600"
            aria-label={showPassword ? "Hide password" : "Show password"}
          >
            {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>
      </div>

      {errorMessage && (
        <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{errorMessage}</p>
      )}

      <button
        type="submit"
        disabled={isLoading}
        className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand-primary py-2.5 text-sm font-semibold text-white transition hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-70"
      >
        {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Lock className="h-4 w-4" />}
        {isLoading ? "Creating Account..." : "Create Patient Account"}
      </button>
    </form>
  );
}
