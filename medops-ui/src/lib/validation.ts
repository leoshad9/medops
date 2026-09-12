export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const PHONE_REGEX = /^\+?[0-9]{7,15}$/;

export const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,100}$/;

export interface ValidationRule {
  required?: boolean;
  minLength?: number;
  maxLength?: number;
  pattern?: RegExp;
  custom?: (value: string) => string | undefined;
}

export interface ValidationErrors {
  [field: string]: string | undefined;
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
    return "Invalid format";
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
