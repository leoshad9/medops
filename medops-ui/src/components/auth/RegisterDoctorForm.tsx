import { Eye, EyeOff, IdCard, Loader2, Lock, Mail, Phone, Stethoscope, User } from "lucide-react";
import { useState } from "react";
import type { SubmitEvent } from "react";

import type { RegisterDoctorRequest } from "../../types/auth";

interface RegisterDoctorFormProps {
  onSubmit: (request: RegisterDoctorRequest) => void;
  isLoading: boolean;
  errorMessage: string | null;
}

export function RegisterDoctorForm({ onSubmit, isLoading, errorMessage }: Readonly<RegisterDoctorFormProps>) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [fullName, setFullName] = useState("");
  const [specialty, setSpecialty] = useState("");
  const [licenseNumber, setLicenseNumber] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({ email, password, fullName, specialty, licenseNumber, phoneNumber });
  }

  return (
    <form onSubmit={handleSubmit} className="mt-8 space-y-5">
      <div>
        <label htmlFor="doctor-full-name" className="block text-sm font-semibold text-slate-800">
          Full Name
        </label>
        <div className="relative mt-1.5">
          <User className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            id="doctor-full-name"
            type="text"
            autoComplete="name"
            required
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="Dr. Jane Doe"
          />
        </div>
      </div>

      <div>
        <label htmlFor="doctor-email" className="block text-sm font-semibold text-slate-800">
          Email Address
        </label>
        <div className="relative mt-1.5">
          <Mail className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            id="doctor-email"
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
          <label htmlFor="doctor-specialty" className="block text-sm font-semibold text-slate-800">
            Specialty
          </label>
          <div className="relative mt-1.5">
            <Stethoscope className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              id="doctor-specialty"
              type="text"
              required
              value={specialty}
              onChange={(event) => setSpecialty(event.target.value)}
              className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
              placeholder="Cardiology"
            />
          </div>
        </div>

        <div>
          <label htmlFor="doctor-license" className="block text-sm font-semibold text-slate-800">
            License Number
          </label>
          <div className="relative mt-1.5">
            <IdCard className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              id="doctor-license"
              type="text"
              required
              value={licenseNumber}
              onChange={(event) => setLicenseNumber(event.target.value)}
              className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
              placeholder="LIC-000123"
            />
          </div>
        </div>
      </div>

      <div>
        <label htmlFor="doctor-phone" className="block text-sm font-semibold text-slate-800">
          Phone Number
        </label>
        <div className="relative mt-1.5">
          <Phone className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            id="doctor-phone"
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
        <label htmlFor="doctor-password" className="block text-sm font-semibold text-slate-800">
          Password
        </label>
        <div className="relative mt-1.5">
          <Lock className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            id="doctor-password"
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
        {isLoading ? "Creating Account..." : "Create Doctor Account"}
      </button>
    </form>
  );
}
