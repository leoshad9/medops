const FALLBACK_TIME_ZONE = "UTC";

function getHour(date: Date, timeZone: string): number {
  let hour: number | undefined;

  try {
    const hourPart = new Intl.DateTimeFormat("en-US", {
      timeZone,
      hour: "2-digit",
      hourCycle: "h23",
    }).formatToParts(date).find((part) => part.type === "hour");
    hour = Number(hourPart?.value);
  } catch {
    hour = undefined;
  }

  if (hour !== undefined) {
    const normalizedHour = hour === 24 ? 0 : hour;
    if (Number.isInteger(normalizedHour) && normalizedHour >= 0 && normalizedHour < 24) {
      return normalizedHour;
    }
  }

  const utcHour = date.getUTCHours();
  return Number.isInteger(utcHour) ? utcHour : 0;
}

export function getUserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || FALLBACK_TIME_ZONE;
  } catch {
    return FALLBACK_TIME_ZONE;
  }
}

export function getTimeOfDayGreeting(
  date: Date = new Date(),
  timeZone: string = getUserTimeZone(),
): string {
  const hour = getHour(date, timeZone);

  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}
