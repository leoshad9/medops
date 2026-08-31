const CLINIC_ZONE = "Asia/Kolkata";

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
