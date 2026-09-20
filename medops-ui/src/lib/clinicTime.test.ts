import { describe, expect, it } from "vitest";

import {
  calcAge,
  displayGender,
  formatDemographics,
  formatClinicDate,
  formatClinicDateTime,
  formatClinicTime,
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