import { beforeEach, describe, expect, it, vi } from "vitest";

import { api } from "./api";
import { getSessionBootstrap } from "./sessionService";

// The real axios client wires CSRF cookies and a 401-retry interceptor; the bootstrap
// only has to map the server payload, so the client is replaced with a stub.
vi.mock("./api", () => ({
  api: {
    get: vi.fn(),
  },
}));

const mockedGet = api.get as unknown as ReturnType<typeof vi.fn>;

function patientPayload() {
  return {
    email: "patient@medops.dev",
    role: "PATIENT",
    patientProfile: {
      id: "p-1",
      email: "patient@medops.dev",
      fullName: "Jane Doe",
      mrn: "MRN-2026-000001",
      dateOfBirth: "1990-01-01",
      gender: "FEMALE",
      phoneNumber: "+12345678901",
      bloodGroup: "O+",
      address: null,
      emergencyContact: null,
      insuranceProvider: null,
      insurancePolicyNumber: null,
    },
    doctorProfile: null,
  };
}

function doctorPayload() {
  return {
    email: "doctor@medops.dev",
    role: "DOCTOR",
    patientProfile: null,
    doctorProfile: {
      email: "doctor@medops.dev",
      fullName: "Dr. Ada Lovelace",
      specialty: "Cardiology",
      licenseNumber: "LIC-12345",
      phoneNumber: "+10987654321",
    },
  };
}

describe("getSessionBootstrap", () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  it("maps the patient profile returned by /v1/me", async () => {
    mockedGet.mockResolvedValue({ data: { data: patientPayload() } });

    const session = await getSessionBootstrap();

    expect(mockedGet).toHaveBeenCalledWith("/v1/me");
    expect(session.email).toBe("patient@medops.dev");
    expect(session.role).toBe("PATIENT");
    expect(session.patientProfile?.name).toBe("Jane Doe");
    expect(session.patientProfile?.bloodGroup).toBe("O+");
    expect(session.doctorProfile).toBeNull();
  });

  it("maps the doctor profile returned by /v1/me", async () => {
    mockedGet.mockResolvedValue({ data: { data: doctorPayload() } });

    const session = await getSessionBootstrap();

    expect(session.role).toBe("DOCTOR");
    expect(session.doctorProfile?.name).toBe("Dr. Ada Lovelace");
    expect(session.doctorProfile?.specialty).toBe("Cardiology");
    expect(session.patientProfile).toBeNull();
  });

  it("throws when the server returns no payload", async () => {
    mockedGet.mockResolvedValue({ data: { data: null } });

    await expect(getSessionBootstrap()).rejects.toThrow("Session bootstrap returned no data.");
  });
});
