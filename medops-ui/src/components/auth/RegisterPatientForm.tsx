import { Calendar, Eye, EyeOff, Loader2, Lock, Mail, Phone, User, UserRound } from "lucide-react";
import { useState } from "react";
import type { SubmitEvent } from "react";

import type { Gender, RegisterPatientRequest } from "../../types/auth";
import {
  EMAIL_REGEX,
  PHONE_REGEX,
  PASSWORD_REGEX,
  type ValidationErrors,
  type ValidationRule,
  validateField,
  validateForm,
} from "../../lib/validation";

interface RegisterPatientFormProps {
  onSubmit: (request: RegisterPatientRequest) => void;
  isLoading: boolean;
  errorMessage: string | null;
}

const validationRules: Record<string, ValidationRule> = {
  email: { required: true, pattern: EMAIL_REGEX },
  password: { required: true, pattern: PASSWORD_REGEX },
  fullName: { required: true, maxLength: 255 },
  dateOfBirth: {
    required: true,
    custom: (value: string) => {
      if (!value) return "Date of birth is required";
      const inputDate = new Date(value);
      const today = new Date();
      if (inputDate >= today) return "Date of birth must be in the past";
      return undefined;
    },
  },
  phoneNumber: { required: true, pattern: PHONE_REGEX },
};

export function RegisterPatientForm({ onSubmit, isLoading, errorMessage }: Readonly<RegisterPatientFormProps>) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [fullName, setFullName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [gender, setGender] = useState<Gender>("FEMALE");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [errors, setErrors] = useState<ValidationErrors>({});

  function validateFieldOnBlur(field: string, value: string) {
    setErrors((prev) => ({ ...prev, [field]: validateField(value, validationRules[field]) }));
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values: Record<string, string> = { email, password, fullName, dateOfBirth, phoneNumber };
    const newErrors = validateForm(values, validationRules);
    setErrors(newErrors);

    const hasErrors = Object.values(newErrors).some((e) => e !== undefined);
    if (hasErrors) return;

    onSubmit({ email, password, fullName, dateOfBirth, gender, phoneNumber });
  }

  function updateField(field: string, value: string) {
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
    switch (field) {
      case "email": setEmail(value); break;
      case "password": setPassword(value); break;
      case "fullName": setFullName(value); break;
      case "dateOfBirth": setDateOfBirth(value); break;
      case "phoneNumber": setPhoneNumber(value); break;
    }
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
            onChange={(event) => updateField("fullName", event.target.value)}
            onBlur={(event) => validateFieldOnBlur("fullName", event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="Enter your full name"
          />
        </div>
        {errors.fullName && <p className="mt-1 text-xs text-red-600">{errors.fullName}</p>}
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
              max={new Date().toISOString().split("T")[0]}
              onChange={(event) => updateField("dateOfBirth", event.target.value)}
              onBlur={(event) => validateFieldOnBlur("dateOfBirth", event.target.value)}
              className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            />
          </div>
          {errors.dateOfBirth && <p className="mt-1 text-xs text-red-600">{errors.dateOfBirth}</p>}
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
            onChange={(event) => updateField("phoneNumber", event.target.value)}
            onBlur={(event) => validateFieldOnBlur("phoneNumber", event.target.value)}
            className="w-full rounded-lg border border-slate-300 py-2.5 pr-3 pl-10 text-sm text-slate-900 outline-none focus:border-brand-primary focus:ring-1 focus:ring-brand-primary"
            placeholder="+1 234 567 8901"
          />
        </div>
        {errors.phoneNumber && <p className="mt-1 text-xs text-red-600">{errors.phoneNumber}</p>}
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
            <li>• At least 8 characters with uppercase, lowercase, digit &amp; special character</li>
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
        {isLoading ? "Creating Account..." : "Create Patient Account"}
      </button>
    </form>
  );
}
