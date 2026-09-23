import { describe, expect, it } from "vitest";

import {
  calcAge,
  displayGender,
  formatDemographics,
  formatClinicDate,
  formatClinicDateTime,
  formatClinicTime,
  formatPatientDateTime,
  formatPatientDateTimeAbsolute,
  formatPatientTime,
  formatRelativeWithAbsolute,
  formatTimeRemaining,
  sanitizePatientField,
  createCalendarDataUri,
  createGoogleCalendarUrl,
} from "./clinicTime";

describe("calcAge", () => {
  const today = "2026-09-20";

  it("computes whole years from an ISO date of birth", () => {
    expect(calcAge("1990-01-15", today)).toBe(36);
  });

  it("does not count the current year before the birthday", () => {
    expect(calcAge("1990-12-01", today)).toBe(35);
  });

  it("counts the birthday on the day itself", () => {
    expect(calcAge("1990-09-20", today)).toBe(36);
  });

  it("returns null for a missing date of birth", () => {
    expect(calcAge(null, today)).toBeNull();
    expect(calcAge(undefined, today)).toBeNull();
    expect(calcAge("", today)).toBeNull();
  });

  it("returns null for malformed dates", () => {
    expect(calcAge("14 Mar 1985", today)).toBeNull();
    expect(calcAge("1990-13-01", today)).toBeNull();
  });

  it("returns null for future dates of birth", () => {
    expect(calcAge("2030-01-01", today)).toBeNull();
  });

  it("returns null for implausible ages", () => {
    expect(calcAge("1800-01-01", today)).toBeNull();
  });
});

describe("formatDemographics", () => {
  it("renders age and title-cased gender", () => {
    expect(formatDemographics(41, "FEMALE")).toBe("41 yrs · Female");
  });

  it("renders age without gender", () => {
    expect(formatDemographics(41, null)).toBe("41 yrs");
  });

  it("renders a gender-only row without a fake age", () => {
    expect(formatDemographics(null, "MALE")).toBe("Age not recorded · Male");
  });

  it("renders an explicit unrecorded state when everything is missing", () => {
    expect(formatDemographics(null, null)).toBe("Not recorded");
  });
});

describe("displayGender", () => {
  it("title-cases API enum values", () => {
    expect(displayGender("FEMALE")).toBe("Female");
    expect(displayGender("OTHER")).toBe("Other");
  });

  it("passes through empty values", () => {
    expect(displayGender("")).toBe("");
  });
});

describe("formatClinicDate", () => {
  it("formats ISO dates in the clinic zone", () => {
    expect(formatClinicDate("1990-01-01")).toBe("1 Jan 1990");
  });

  it("passes legacy display-form dates through untouched", () => {
    expect(formatClinicDate("14 Mar 1985")).toBe("14 Mar 1985");
  });
});

describe("clinic timezone formatting", () => {
  // 2026-08-26T05:00:00Z is 10:30 in Asia/Kolkata.
  const instant = "2026-08-26T05:00:00Z";

  it("formats times in the clinic zone", () => {
    expect(formatClinicTime(instant)).toMatch(/10:30/);
  });

  it("formats date-times in the clinic zone", () => {
    expect(formatClinicDateTime(instant)).toMatch(/10:30/);
  });
});

describe("patient timezone formatting (UTC fixture)", () => {
  const TZ = "UTC";
  // 2026-09-30T08:00:00Z is 8:00 AM UTC / Wed 30 Sep 2026.
  const instant = "2026-09-30T08:00:00Z";

  it("formats full date+time as 'Wed, 30 Sep 2026 at 8:00 AM'", () => {
    expect(formatPatientDateTime(instant, TZ)).toBe("Wed, 30 Sep 2026 at 8:00 AM");
  });

  it("formats short absolute date+time as '30 Sep 2026, 8:00 AM'", () => {
    expect(formatPatientDateTimeAbsolute(instant, TZ)).toBe("30 Sep 2026, 8:00 AM");
  });

  it("formats time-of-day only as '8:00 AM'", () => {
    expect(formatPatientTime(instant, TZ)).toBe("8:00 AM");
  });

  it("never includes a raw ISO UTC string", () => {
    expect(formatPatientDateTime(instant, TZ)).not.toMatch(/T\d{2}:\d{2}:\d{2}/);
  });

  it("renders PM hours without a leading zero", () => {
    expect(formatPatientTime("2026-09-30T21:30:00Z", TZ)).toBe("9:30 PM");
  });
});

describe("formatRelativeWithAbsolute", () => {
  const TZ = "UTC";
  const now = Date.parse("2026-09-22T10:00:00Z");

  it("shows relative age plus the exact local time", () => {
    // 20 hours before now -> "20 hours ago · 21 Sep 2026, 2:00 PM"
    expect(formatRelativeWithAbsolute("2026-09-21T14:00:00Z", TZ, now)).toBe(
      "20 hours ago · 21 Sep 2026, 2:00 PM",
    );
  });
});

describe("formatTimeRemaining", () => {
  const TZ = "UTC";
  const now = Date.parse("2026-09-22T10:00:00Z");

  it("returns 'Now' for a past appointment", () => {
    expect(formatTimeRemaining("2026-09-22T09:00:00Z", now, TZ)).toBe("Now");
  });

  it("returns a relative label for an hour-scale future appointment", () => {
    expect(formatTimeRemaining("2026-09-22T12:00:00Z", now, TZ)).toBe("in 2 hours");
  });

  it("returns 'Today at …' for a same-day appointment", () => {
    expect(formatTimeRemaining("2026-09-22T21:30:00Z", now, TZ)).toBe("Today at 9:30 PM");
  });

  it("returns 'Tomorrow at …' for a next-day appointment", () => {
    expect(formatTimeRemaining("2026-09-23T21:30:00Z", now, TZ)).toBe("Tomorrow at 9:30 PM");
  });
});

describe("sanitizePatientField", () => {
  it("returns undefined for null/empty", () => {
    expect(sanitizePatientField(null)).toBeUndefined();
    expect(sanitizePatientField("   ")).toBeUndefined();
  });

  it("drops known test/internal strings", () => {
    expect(sanitizePatientField("Testing bot")).toBeUndefined();
    expect(sanitizePatientField("Dr. Mohd: staff only notes")).toBeUndefined();
    expect(sanitizePatientField("  INTERNAL USE ONLY  ")).toBeUndefined();
  });

  it("strips a leading clinician role prefix but keeps the note", () => {
    expect(sanitizePatientField("Dr. Mohd Adnan: Routine check-up")).toBe("Routine check-up");
  });

  it("passes patient-safe text through trimmed", () => {
    expect(sanitizePatientField("  Annual physical  ")).toBe("Annual physical");
  });
});

describe("createCalendarDataUri", () => {
  it("produces a downloadable ICS data URI", () => {
    const uri = createCalendarDataUri({
      id: "apt-123",
      startsAt: "2026-09-30T08:00:00Z",
      endsAt: "2026-09-30T08:30:00Z",
      summary: "Appointment with Dr. Mohd Adnan",
      location: "Clinic",
      description: "Annual check-up",
    });
    expect(uri).toMatch(/^data:text\/calendar;charset=utf-8;base64,/);
    const decoded = atob(uri.split(",")[1]);
    expect(decoded).toContain("BEGIN:VCALENDAR");
    expect(decoded).toContain("DTSTART:20260930T080000Z");
    expect(decoded).toContain("SUMMARY:Appointment with Dr. Mohd Adnan");
    expect(decoded).toContain("LOCATION:Clinic");
  });
});

describe("createGoogleCalendarUrl", () => {
  it("builds a Google Calendar add-event URL with the appointment window", () => {
    const url = createGoogleCalendarUrl({
      id: "apt-123",
      startsAt: "2026-09-30T08:00:00Z",
      endsAt: "2026-09-30T08:30:00Z",
      summary: "Appointment with Dr. Mohd Adnan",
      location: "Clinic",
      description: "Annual check-up",
    });
    expect(url).toContain("https://calendar.google.com/calendar/render?");
    expect(url).toContain("action=TEMPLATE");
    expect(url).toContain("dates=20260930T080000Z%2F20260930T083000Z");
    expect(url).toContain("text=Appointment+with+Dr.+Mohd+Adnan");
    expect(url).toContain("location=Clinic");
    expect(url).toContain("details=Annual+check-up");
  });

  it("omits empty optional fields instead of sending them as blank", () => {
    const url = createGoogleCalendarUrl({
      id: "apt-1",
      startsAt: "2026-09-30T08:00:00Z",
      endsAt: "2026-09-30T08:30:00Z",
      summary: "Visit",
    });
    expect(url).not.toContain("location=");
    expect(url).not.toContain("details=");
  });
});