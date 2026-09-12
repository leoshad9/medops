export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const PHONE_REGEX = /^\+?[0-9]{7,15}$/;

export const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,100}$/;

export const NAME_REGEX = /^[\p{L}\s.'-]+$/u;

export interface ValidationRule {
  required?: boolean;
  minLength?: number;
  maxLength?: number;
  pattern?: RegExp;
  errorMessage?: string;
  custom?: (value: string) => string | undefined;
}

export interface ValidationErrors {
  [field: string]: string | undefined;
}

export interface PasswordRequirement {
  label: string;
  test: (value: string) => boolean;
}

export const PASSWORD_REQUIREMENTS: PasswordRequirement[] = [
  { label: "At least 8 characters", test: (v) => v.length >= 8 },
  { label: "Contains uppercase letter", test: (v) => /[A-Z]/.test(v) },
  { label: "Contains lowercase letter", test: (v) => /[a-z]/.test(v) },
  { label: "Contains a digit", test: (v) => /\d/.test(v) },
  { label: "Contains a special character", test: (v) => /[^A-Za-z0-9]/.test(v) },
];

export function checkPasswordRequirements(value: string): PasswordRequirement[] {
  return PASSWORD_REQUIREMENTS.filter((req) => !req.test(value));
}

export function validateField(value: string, rule: ValidationRule): string | undefined {
  if (rule.required && !value.trim()) {
    return "This field is required";
  }
  if (rule.minLength && value.length < rule.minLength) {
    return `Must be at least ${rule.minLength} characters`;
  }
  if (rule.maxLength && value.length > rule.maxLength) {
    return `Must be at most ${rule.maxLength} characters`;
  }
  if (rule.pattern && !rule.pattern.test(value)) {
    return rule.errorMessage || "Invalid format";
  }
  if (rule.custom) {
    return rule.custom(value);
  }
  return undefined;
}

export function validateForm(
  values: Record<string, string>,
  rules: Record<string, ValidationRule>
): ValidationErrors {
  const errors: ValidationErrors = {};
  for (const [field, rule] of Object.entries(rules)) {
    errors[field] = validateField(values[field] || "", rule);
  }
  return errors;
}
