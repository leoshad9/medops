import { Eye, EyeOff, IdCard, Loader2, Lock, Mail, Phone, Stethoscope, User } from "lucide-react";
import { useState } from "react";
import type { SubmitEvent } from "react";

import type { RegisterDoctorRequest } from "../../types/auth";
import {
  EMAIL_REGEX,
  NAME_REGEX,
  PHONE_REGEX,
  PASSWORD_REGEX,
  checkPasswordRequirements,
  type ValidationErrors,
  type ValidationRule,
  validateField,
  validateForm,
} from "../../lib/validation";

interface RegisterDoctorFormProps {
  onSubmit: (request: RegisterDoctorRequest) => void;
  isLoading: boolean;
  errorMessage: string | null;
}

const validationRules: Record<string, ValidationRule> = {
  email: {
    required: true,
    pattern: EMAIL_REGEX,
    errorMessage: "Please enter a valid email address",
  },
  password: {
    required: true,
    minLength: 8,
    maxLength: 100,
    pattern: PASSWORD_REGEX,
    errorMessage: "Password must contain uppercase, lowercase, digit & special character",
  },
  fullName: {
    required: true,
    maxLength: 255,
    pattern: NAME_REGEX,
    errorMessage: "Full name may only contain letters, spaces, hyphens, apostrophes, and periods",
  },
  specialty: {
    required: true,
    maxLength: 255,
    errorMessage: "Specialty must be at most 255 characters",
  },
  licenseNumber: {
    required: true,
    maxLength: 100,
    errorMessage: "License number must be at most 100 characters",
  },
  phoneNumber: {
    required: true,
    pattern: PHONE_REGEX,
    errorMessage: "Enter a valid phone number (7-15 digits, optionally prefixed with +)",
  },
};

export function RegisterDoctorForm({ onSubmit, isLoading, errorMessage }: Readonly<RegisterDoctorFormProps>) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [fullName, setFullName] = useState("");
  const [specialty, setSpecialty] = useState("");
  const [licenseNumber, setLicenseNumber] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [errors, setErrors] = useState<ValidationErrors>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});

  function validateFieldOnBlur(field: string, value: string) {
    setTouched((prev) => ({ ...prev, [field]: true }));
    setErrors((prev) => ({ ...prev, [field]: validateField(value, validationRules[field]) }));
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values: Record<string, string> = { email, password, fullName, specialty, licenseNumber, phoneNumber };
    const newErrors = validateForm(values, validationRules);
    setErrors(newErrors);

    const hasErrors = Object.values(newErrors).some((e) => e !== undefined);
    if (hasErrors) return;

    onSubmit({ email, password, fullName, specialty, licenseNumber, phoneNumber });
  }

  function updateField(field: string, value: string) {
    if (touched[field]) {
      setErrors((prev) => ({ ...prev, [field]: validateField(value, validationRules[field]) }));
    } else if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
    switch (field) {
      case "email": setEmail(value); break;
      case "password": setPassword(value); break;
      case "fullName": setFullName(value); break;
      case "specialty": setSpecialty(value); break;
      case "licenseNumber": setLicenseNumber(value); break;
      case "phoneNumber": setPhoneNumber(value); break;
    }
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
            onChange={(event) => updateField("fullName", event.target.value)}
            onBlur={(event) => validateFieldOnBlur("fullName", event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="Dr. Jane Doe"
          />
        </div>
        {errors.fullName && <p className="mt-1 text-xs text-red-600">{errors.fullName}</p>}
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
            onChange={(event) => updateField("email", event.target.value)}
            onBlur={(event) => validateFieldOnBlur("email", event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="Enter your email"
          />
        </div>
        {errors.email && <p className="mt-1 text-xs text-red-600">{errors.email}</p>}
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
              onChange={(event) => updateField("specialty", event.target.value)}
              onBlur={(event) => validateFieldOnBlur("specialty", event.target.value)}
              className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
              placeholder="Cardiology"
            />
          </div>
          {errors.specialty && <p className="mt-1 text-xs text-red-600">{errors.specialty}</p>}
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
              onChange={(event) => updateField("licenseNumber", event.target.value)}
              onBlur={(event) => validateFieldOnBlur("licenseNumber", event.target.value)}
              className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
              placeholder="LIC-000123"
            />
          </div>
          {errors.licenseNumber && <p className="mt-1 text-xs text-red-600">{errors.licenseNumber}</p>}
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
            onChange={(event) => updateField("phoneNumber", event.target.value)}
            onBlur={(event) => validateFieldOnBlur("phoneNumber", event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="+1 234 567 8901"
          />
        </div>
        {errors.phoneNumber && <p className="mt-1 text-xs text-red-600">{errors.phoneNumber}</p>}
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
            onChange={(event) => updateField("password", event.target.value)}
            onBlur={(event) => validateFieldOnBlur("password", event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-10 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="At least 8 characters with uppercase, lowercase, digit & special char"
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
        {errors.password && (
          <ul className="mt-1 space-y-0.5 text-xs text-red-600">
            <li>• {errors.password}</li>
          </ul>
        )}
        {!errors.password && password && (
          <ul className="mt-1 space-y-0.5 text-xs text-slate-500">
            {checkPasswordRequirements(password).map((req) => (
              <li key={req.label}>• {req.label}</li>
            ))}
          </ul>
        )}
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
