import { getUserTimeZone } from "./greeting";

/**
 * IANA zone used for clinician-facing date presentation and "today" bucketing
 * (doctor/admin views). Configurable per deployment via VITE_CLINIC_TIMEZONE;
 * defaults to the original clinic zone. Patient-facing display uses the
 * patient's own timezone via {@link patientTimeZone} instead.
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

const upcomingParts = (timeZone: string) =>
  new Intl.DateTimeFormat("en-GB", {
    timeZone,
    day: "2-digit",
    month: "short",
    year: "numeric",
    weekday: "short",
  });

export function upcomingCardParts(
  isoInstant: string,
  timeZone: string = CLINIC_ZONE,
): {
  day: string;
  month: string;
  weekday: string;
} {
  const parts = upcomingParts(timeZone).formatToParts(new Date(isoInstant));
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return {
    day: value("day"),
    month: `${value("month")} ${value("year")}`.toUpperCase(),
    weekday: value("weekday").toUpperCase(),
  };
}

export function nextAppointmentStat(
  isoInstant: string,
  timeZone: string = CLINIC_ZONE,
): { value: string; sublabel: string } {
  const today = clinicTodayYmd(timeZone);
  const ymd = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(isoInstant));
  const time = formatClinicTime(isoInstant);
  const tomorrow = shiftYmd(today, 1, timeZone);
  if (ymd === today) {
    return { value: "Today", sublabel: time };
  }
  if (ymd === tomorrow) {
    return { value: "Tomorrow", sublabel: time };
  }
  return { value: ymd, sublabel: time };
}

export function clinicTodayYmd(timeZone: string = CLINIC_ZONE): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone,
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

// ---------------------------------------------------------------------------
// Patient-facing formatting (patient's own timezone, browser-resolved)
// ---------------------------------------------------------------------------
//
// Backend timestamps are always stored and returned as UTC instants. The
// patient's configured timezone is the browser timezone (getUserTimeZone),
// which is also the zone shown in the dashboard header. Every patient-visible
// timestamp is rendered through the helpers below so no raw ISO UTC string is
// ever presented and times stay consistent across the dashboard, the
// appointment list, reminder messages, and the detail pages.

/** The IANA timezone of the current patient (browser-resolved, e.g. "Asia/Kolkata"). */
export function patientTimeZone(): string {
  return getUserTimeZone();
}

const patientFormatter = (timeZone: string, options: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat("en-GB", { timeZone, ...options });

/** Full, readable date+time: "Wed, 30 Sep 2026 at 1:30 PM". */
export function formatPatientDateTime(isoInstant: string, timeZone: string = patientTimeZone()): string {
  const date = new Date(isoInstant);
  const datePart = patientFormatter(timeZone, {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
  })
    .format(date)
    .replace(/\bSept\b/g, "Sep");
  const timePart = patientFormatter(timeZone, {
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  })
    .format(date)
    .replace(/\b(am|pm)\b/gi, (m) => m.toUpperCase());
  return `${datePart} at ${timePart}`;
}

/** Short absolute date+time without a weekday: "22 Sep 2026, 5:30 PM". */
export function formatPatientDateTimeAbsolute(
  isoInstant: string,
  timeZone: string = patientTimeZone(),
): string {
  return patientFormatter(timeZone, {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  })
    .format(new Date(isoInstant))
    .replace(/\bSept\b/g, "Sep")
    .replace(/\b(am|pm)\b/gi, (m) => m.toUpperCase());
}

/** Time-of-day only, e.g. "1:30 PM". */
export function formatPatientTime(isoInstant: string, timeZone: string = patientTimeZone()): string {
  return patientFormatter(timeZone, {
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  })
    .format(new Date(isoInstant))
    .replace(/\b(am|pm)\b/gi, (m) => m.toUpperCase());
}

/** Weekday + date only, e.g. "Wed, 30 Sep 2026". */
export function formatPatientDate(isoInstant: string, timeZone: string = patientTimeZone()): string {
  return patientFormatter(timeZone, {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
  })
    .format(new Date(isoInstant))
    .replace(/\bSept\b/g, "Sep");
}

const patientRelativeFormatter = new Intl.RelativeTimeFormat("en", { numeric: "auto", style: "long" });

export function relativePart(isoInstant: string, nowMs: number = Date.now()): string {
  const seconds = Math.round((Date.parse(isoInstant) - nowMs) / 1000);
  const absolute = Math.abs(seconds);
  if (absolute < 60) {
    return patientRelativeFormatter.format(seconds, "second");
  }
  if (absolute < 3600) {
    return patientRelativeFormatter.format(Math.round(seconds / 60), "minute");
  }
  if (absolute < 86400) {
    return patientRelativeFormatter.format(Math.round(seconds / 60 / 60), "hour");
  }
  if (absolute < 604800) {
    return patientRelativeFormatter.format(Math.round(seconds / 60 / 60 / 24), "day");
  }
  return formatPatientDateTimeAbsolute(isoInstant);
}

/**
 * Patient-friendly timestamp combining a relative age with the exact local
 * time, e.g. "20 hours ago · 22 Sep 2026, 5:30 PM".
 */
export function formatRelativeWithAbsolute(
  isoInstant: string,
  timeZone: string = patientTimeZone(),
  nowMs: number = Date.now(),
): string {
  return `${relativePart(isoInstant, nowMs)} · ${formatPatientDateTimeAbsolute(isoInstant, timeZone)}`;
}

/**
 * Short humanised "time remaining" for an upcoming appointment, e.g.
 * "in 2 hours", "Today at 1:30 PM", "Tomorrow at 9:00 AM", or a full date.
 */
export function formatTimeRemaining(
  isoInstant: string,
  nowMs: number = Date.now(),
  timeZone: string = patientTimeZone(),
): string {
  const diffSeconds = Math.round((Date.parse(isoInstant) - nowMs) / 1000);
  if (diffSeconds <= 0) {
    return "Now";
  }
  if (diffSeconds < 60) {
    return `in ${diffSeconds} sec`;
  }
  if (diffSeconds < 3600) {
    return patientRelativeFormatter.format(Math.round(diffSeconds / 60), "minute");
  }
  if (diffSeconds < 4 * 3600) {
    return patientRelativeFormatter.format(Math.round(diffSeconds / 3600), "hour");
  }

  const ymd = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(nowMs);
  const targetYmd = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(isoInstant));
  if (targetYmd === ymd) {
    return `Today at ${formatPatientTime(isoInstant, timeZone)}`;
  }
  const tomorrow = shiftYmd(ymd, 1, timeZone);
  if (targetYmd === tomorrow) {
    return `Tomorrow at ${formatPatientTime(isoInstant, timeZone)}`;
  }
  if (diffSeconds < 86400) {
    return patientRelativeFormatter.format(Math.round(diffSeconds / 60 / 60), "hour");
  }
  return formatPatientDateTime(isoInstant, timeZone);
}

function shiftYmd(ymd: string, days: number, timeZone: string): string {
  const y = Number(ymd.slice(0, 4));
  const m = Number(ymd.slice(5, 7));
  const d = Number(ymd.slice(8, 10));
  const localDate = new Date(Date.UTC(y, m - 1, d));
  localDate.setUTCDate(localDate.getUTCDate() + days);
  return new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(localDate);
}

// Substrings that indicate clinician-only, internal, test, or bot content that
// must never be surfaced to a patient. Matched case-insensitively.
const PATIENT_UNSAFE_PATTERNS = [
  "testing bot",
  "test bot",
  "medops bot",
  "staff only",
  "internal use only",
  "clinician note",
  "private note",
];

/**
 * Returns a patient-safe version of a free-text field (e.g. an appointment
 * reason). Strips leading role prefixes ("Dr. X:", "Nurse:") and drops values
 * that look like test/internal/bot content, returning `undefined` so callers can
 * omit the field entirely instead of leaking internal notes.
 */
export function sanitizePatientField(value: string | null | undefined): string | undefined {
  if (!value) {
    return undefined;
  }
  const trimmed = value.trim().replace(/^(dr\.?|nurse|clinician|admin)[^:,.-]*[:|-]\s*/i, "").trim();
  const lower = trimmed.toLowerCase();
  if (!trimmed || PATIENT_UNSAFE_PATTERNS.some((p) => lower.includes(p))) {
    return undefined;
  }
  return trimmed;
}

/** A calendar event built from an appointment, for the "Add to Calendar" action. */
export interface CalendarEvent {
  id: string;
  startsAt: string;
  endsAt: string;
  summary: string;
  location?: string;
  description?: string;
}

function toIcsUtc(isoInstant: string): string {
  // 2026-09-30T08:00:00.000Z -> 20260930T080000Z
  return new Date(isoInstant)
    .toISOString()
    .replace(/[-:]/g, "")
    .replace(/\.\d{3}/, "");
}

/**
 * Builds a Google Calendar "add event" URL that opens the event editor in a
 * new tab instead of silently downloading an ICS file.
 *
 * Google's format: https://calendar.google.com/calendar/render?action=TEMPLATE
 *                  &text=<summary>&dates=<start>/<end>&location=<loc>&details=<desc>
 * Dates are YYYYMMDDTHHMMSSZ (UTC). Times are always UTC here — the appointment
 * window is stored as an ISO-8601 instant and Google renders the editor in the
 * viewer's own time zone.
 *
 * URLSearchParams handles all encoding; values are trimmed first so an empty
 * location/description is omitted entirely rather than sent as a blank query.
 */
export function createGoogleCalendarUrl(event: CalendarEvent): string {
  const start = toIcsUtc(event.startsAt);
  const end = toIcsUtc(event.endsAt);
  const params = new URLSearchParams({
    action: "TEMPLATE",
    text: event.summary.trim(),
    dates: `${start}/${end}`,
  });
  const location = event.location?.trim();
  if (location) {
    params.set("location", location);
  }
  const description = event.description?.trim();
  if (description) {
    params.set("details", description);
  }
  return `https://calendar.google.com/calendar/render?${params.toString()}`;
}

/** Builds a one-click "Add to Calendar" ICS data URI from a UTC appointment window. */
function escapeIcsText(value: string): string {
  return value
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n");
}

export function createCalendarDataUri(event: CalendarEvent): string {
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//MedOps//Patient Portal//EN",
    "CALSCALE:GREGORIAN",
    "BEGIN:VEVENT",
    `UID:${event.id}@medops`,
    `DTSTAMP:${toIcsUtc(new Date().toISOString())}`,
    `DTSTART:${toIcsUtc(event.startsAt)}`,
    `DTEND:${toIcsUtc(event.endsAt)}`,
    `SUMMARY:${escapeIcsText(event.summary)}`,
  ];
  if (event.location) {
    lines.push(`LOCATION:${escapeIcsText(event.location)}`);
  }
  if (event.description) {
    lines.push(`DESCRIPTION:${escapeIcsText(event.description)}`);
  }
  lines.push("END:VEVENT", "END:VCALENDAR");
  const ics = lines.join("\r\n");
  return `data:text/calendar;charset=utf-8;base64,${btoa(ics)}`;
}
