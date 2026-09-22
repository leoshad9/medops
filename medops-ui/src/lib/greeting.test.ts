import { describe, expect, it } from "vitest";

import { getTimeOfDayGreeting } from "./greeting";

describe("getTimeOfDayGreeting", () => {
  it.each([
    ["2026-01-01T00:00:00Z", "Good morning"],
    ["2026-01-01T11:59:59Z", "Good morning"],
    ["2026-01-01T12:00:00Z", "Good afternoon"],
    ["2026-01-01T16:59:59Z", "Good afternoon"],
    ["2026-01-01T17:00:00Z", "Good evening"],
    ["2026-01-01T23:59:59Z", "Good evening"],
  ])("uses UTC day-part boundaries", (instant, expected) => {
    expect(getTimeOfDayGreeting(new Date(instant), "UTC")).toBe(expected);
  });

  it.each([
    ["2026-01-01T06:29:59Z", "Good morning"],
    ["2026-01-01T06:30:00Z", "Good afternoon"],
    ["2026-01-01T11:29:59Z", "Good afternoon"],
    ["2026-01-01T11:30:00Z", "Good evening"],
  ])("calculates the hour in the requested timezone", (instant, expected) => {
    expect(getTimeOfDayGreeting(new Date(instant), "Asia/Kolkata")).toBe(expected);
  });

  it("falls back to UTC for an unavailable timezone", () => {
    expect(getTimeOfDayGreeting(new Date("2026-01-01T12:00:00Z"), "Not/AZone")).toBe(
      "Good afternoon",
    );
  });
});
