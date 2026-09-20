/**
 * IANA zone used for all clinical date presentation and "today" bucketing.
 * Configurable per deployment via VITE_CLINIC_TIMEZONE so clinicians in other
 * regions do not see hardcoded IST times; defaults to the original clinic zone.
 */
const CLINIC_ZONE = import.meta.env.VITE_CLINIC_TIMEZONE || "Asia/Kolkata";

export const CLINIC_TIMEZONE = CLINIC_ZONE;

const dateTimeFormatter = new Intl.DateTimeFormat("en-IN", {
  timeZone: CLINIC_ZONE,
  weekday: "short",
  day: "2-digit",
  month: "short",
  year: "numeric",
  hour: "numeric",
  minute: "2-digit",
  hour12: true,
});

const timeFormatter = new Intl.DateTimeFormat("en-IN", {
  timeZone: CLINIC_ZONE,
  hour: "numeric",
  minute: "2-digit",
  hour12: true,
});

export function formatClinicDateTime(isoInstant: string): string {
  return dateTimeFormatter.format(new Date(isoInstant));
}

export function formatClinicTime(isoInstant: string): string {
  return timeFormatter.format(new Date(isoInstant));
}

const dateOnlyParts = new Intl.DateTimeFormat("en-GB", {
  timeZone: CLINIC_ZONE,
  day: "numeric",
  month: "short",
  year: "numeric",
});

const ISO_YMD = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Formats a date-of-birth for display. Backend sends LocalDate as ISO "1990-01-01";
 * pass values already in display form (e.g. legacy mock data "14 Mar 1985") through
 * untouched.
 */
export function formatClinicDate(value: string): string {
  if (!ISO_YMD.test(value)) {
    return value;
  }
  return dateOnlyParts.format(new Date(`${value}T00:00:00+05:30`));
}

/**
 * Title-cases a Gender enum value from the API ("FEMALE" -> "Female").
 * Values already in display form are passed through untouched.
 */
export function displayGender(value: string): string {
  if (value.length === 0 || value === value.toLowerCase()) {
    return value;
  }
  return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
}

/**
 * Whole-year age computed from an ISO date-of-birth ("yyyy-MM-dd") against a
 * clinic "today" (defaults to the clinic zone's today). Returns null when the
 * DOB is absent or malformed so callers can render "Not recorded" instead of a
 * misleading zero.
 */
export function calcAge(
  dateOfBirth: string | null | undefined,
  todayYmd: string = clinicTodayYmd(),
): number | null {
  if (!dateOfBirth || !ISO_YMD.test(dateOfBirth) || !ISO_YMD.test(todayYmd)) {
    return null;
  }
  const [birthY, birthM, birthD] = dateOfBirth.split("-").map(Number);
  const [todayY, todayM, todayD] = todayYmd.split("-").map(Number);
  if (!birthY || birthM < 1 || birthM > 12 || birthD < 1 || birthD > 31) {
    return null;
  }
  let age = todayY - birthY;
  if (todayM < birthM || (todayM === birthM && todayD < birthD)) {
    age -= 1;
  }
  return age >= 0 && age < 130 ? age : null;
}

/**
 * Renders the demographics cell value ("41 yrs · Female"). Missing data is
 * surfaced explicitly rather than as a default-looking "0 yrs · —".
 */
export function formatDemographics(
  age: number | null,
  gender: string | null | undefined,
): string {
  if (age === null && !gender) {
    return "Not recorded";
  }
  const parts = [age === null ? "Age not recorded" : `${age} yrs`];
  if (gender) {
    parts.push(displayGender(gender));
  }
  return parts.join(" · ");
}

const upcomingParts = new Intl.DateTimeFormat("en-GB", {
  timeZone: CLINIC_ZONE,
  day: "2-digit",
  month: "short",
  year: "numeric",
  weekday: "short",
});

export function upcomingCardParts(isoInstant: string): {
  day: string;
  month: string;
  weekday: string;
} {
  const parts = upcomingParts.formatToParts(new Date(isoInstant));
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return {
    day: value("day"),
    month: `${value("month")} ${value("year")}`.toUpperCase(),
    weekday: value("weekday").toUpperCase(),
  };
}

export function nextAppointmentStat(isoInstant: string): { value: string; sublabel: string } {
  const today = clinicTodayYmd();
  const ymd = new Intl.DateTimeFormat("en-CA", {
    timeZone: CLINIC_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(isoInstant));
  const time = formatClinicTime(isoInstant);
  const tomorrow = shiftYmd(today, 1);
  if (ymd === today) {
    return { value: "Today", sublabel: time };
  }
  if (ymd === tomorrow) {
    return { value: "Tomorrow", sublabel: time };
  }
  return { value: ymd, sublabel: time };
}

function shiftYmd(ymd: string, days: number): string {
  const date = new Date(`${ymd}T00:00:00+05:30`);
  date.setDate(date.getDate() + days);
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: CLINIC_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

export function clinicTodayYmd(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: CLINIC_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

export function clinicDayBoundsIso(ymd: string): { from: string; to: string } {
  const from = new Date(`${ymd}T00:00:00+05:30`);
  const next = new Date(from);
  next.setDate(next.getDate() + 1);
  return { from: from.toISOString(), to: next.toISOString() };
}

const relativeFormatter = new Intl.RelativeTimeFormat("en", { numeric: "auto", style: "long" });

/**
 * Human-friendly "time ago" label for notification timestamps, switching to an
 * absolute clinic datetime once the event is more than a week old.
 */
export function relativeTimeAgo(isoInstant: string): string {
  const seconds = Math.round((new Date(isoInstant).getTime() - Date.now()) / 1000);
  const absolute = Math.abs(seconds);
  if (absolute < 60) {
    return relativeFormatter.format(seconds, "second");
  }
  if (absolute < 3600) {
    return relativeFormatter.format(Math.round(seconds / 60), "minute");
  }
  if (absolute < 86400) {
    return relativeFormatter.format(Math.round(seconds / 3600), "hour");
  }
  if (absolute < 604800) {
    return relativeFormatter.format(Math.round(seconds / 86400), "day");
  }
  return formatClinicDateTime(isoInstant);
}
