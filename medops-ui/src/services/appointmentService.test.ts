import { describe, expect, it } from "vitest";

import type { AppointmentDto } from "./appointmentService";
import { deriveUiStatus } from "./appointmentService";

function dto(overrides: Partial<AppointmentDto>): AppointmentDto {
  return {
    id: "apt-1",
    patientId: "p1",
    doctorId: "d1",
    patientName: "Patient One",
    patientMrn: "MRN-1",
    patientDateOfBirth: null,
    patientGender: null,
    doctorName: "Dr. X",
    specialty: "General Medicine",
    reason: null,
    location: null,
    startsAt: "2026-09-20T09:00:00Z",
    endsAt: "2026-09-20T09:30:00Z",
    status: "BOOKED",
    ...overrides,
  };
}

describe("deriveUiStatus", () => {
  it("classifies a future BOOKED slot as UPCOMING", () => {
    const beforeStart = Date.parse("2026-09-20T08:00:00Z");
    expect(deriveUiStatus(dto({ startsAt: "2026-09-20T12:00:00Z", endsAt: "2026-09-20T12:30:00Z" }), beforeStart))
      .toBe("UPCOMING");
  });

  it("classifies a BOOKED slot that has ended as COMPLETED", () => {
    const afterEnd = Date.parse("2026-09-20T09:35:00Z");
    expect(deriveUiStatus(dto({ startsAt: "2026-09-20T09:00:00Z", endsAt: "2026-09-20T09:30:00Z" }), afterEnd))
      .toBe("COMPLETED");
  });

  it("treats the exact end instant as already-ended (COMPLETED)", () => {
    const atEnd = Date.parse("2026-09-20T09:30:00Z");
    expect(deriveUiStatus(dto({ startsAt: "2026-09-20T09:00:00Z", endsAt: "2026-09-20T09:30:00Z" }), atEnd))
      .toBe("COMPLETED");
  });

  it("defaults to the wall clock when no reference time is supplied", () => {
    // endsAt is far in the past relative to "now" → must not stay UPCOMING.
    expect(deriveUiStatus(dto({ startsAt: "2026-09-10T09:00:00Z", endsAt: "2026-09-10T09:30:00Z" })))
      .toBe("COMPLETED");
  });

  it("passes COMPLETED and CANCELLED through unchanged regardless of time", () => {
    const past = Date.parse("2026-09-20T00:00:00Z");
    expect(deriveUiStatus(dto({ status: "COMPLETED" }), past)).toBe("COMPLETED");
    expect(deriveUiStatus(dto({ status: "CANCELLED" }), past)).toBe("CANCELLED");
  });
});
