import type { ApiResponse } from "../types/api";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

export interface DoctorPatientSummary {
  id: string;
  fullName: string;
  mrn: string;
}

export type ReportStatus = "NEW" | "REVIEWED";
export type PrescriptionApiStatus = "ACTIVE" | "DISCONTINUED";

export interface ClinicalReportDto {
  id: string;
  patientId: string;
  doctorId: string;
  patientName: string;
  patientMrn: string;
  doctorName: string;
  title: string;
  notes: string | null;
  status: ReportStatus;
  originalFilename: string;
  sizeBytes: number;
  hasFile: boolean;
  createdAt: string;
  reviewedAt: string | null;
  summary: string | null;
  summarizedAt: string | null;
}

export interface PrescriptionDto {
  id: string;
  patientId: string;
  doctorId: string;
  patientName: string;
  patientMrn: string;
  doctorName: string;
  medicationName: string;
  dosage: string;
  instructions: string;
  refillsRemaining: number;
  status: PrescriptionApiStatus;
  originalFilename: string | null;
  hasFile: boolean;
  createdAt: string;
}

export async function listMyPatients(): Promise<DoctorPatientSummary[]> {
  try {
    const response = await api.get<ApiResponse<DoctorPatientSummary[]>>("/v1/doctors/me/patients");
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load patients. Please try again."));
  }
}

export async function listReports(patientId?: string): Promise<ClinicalReportDto[]> {
  try {
    const response = await api.get<ApiResponse<ClinicalReportDto[]>>("/v1/reports", {
      params: patientId ? { patientId } : undefined,
    });
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load reports. Please try again."));
  }
}

export async function uploadReport(patientId: string, title: string, notes: string, file: File): Promise<ClinicalReportDto> {
  const form = new FormData();
  form.append("title", title);
  if (notes.trim()) {
    form.append("notes", notes.trim());
  }
  form.append("file", file);
  try {
    const response = await api.post<ApiResponse<ClinicalReportDto>>(`/v1/patients/${patientId}/reports`, form, {
      headers: { "Idempotency-Key": crypto.randomUUID() },
    });
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to upload that report. Please try again."));
  }
}

export async function reviewReport(reportId: string): Promise<ClinicalReportDto> {
  try {
    const response = await api.post<ApiResponse<ClinicalReportDto>>(`/v1/reports/${reportId}/review`);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to mark that report as reviewed."));
  }
}

export async function summarizeReport(reportId: string): Promise<ClinicalReportDto> {
  try {
    const response = await api.post<ApiResponse<ClinicalReportDto>>(`/v1/reports/${reportId}/summarize`);
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to summarize that report. Please try again."));
  }
}

export async function listPrescriptions(patientId?: string): Promise<PrescriptionDto[]> {
  try {
    const response = await api.get<ApiResponse<PrescriptionDto[]>>("/v1/prescriptions", {
      params: patientId ? { patientId } : undefined,
    });
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load prescriptions. Please try again."));
  }
}

export async function createPrescription(input: {
  patientId: string;
  medicationName: string;
  dosage: string;
  instructions: string;
  refillsRemaining: number;
}): Promise<PrescriptionDto> {
  try {
    const response = await api.post<ApiResponse<PrescriptionDto>>(
      `/v1/patients/${input.patientId}/prescriptions`,
      {
        medicationName: input.medicationName,
        dosage: input.dosage,
        instructions: input.instructions,
        refillsRemaining: input.refillsRemaining,
      },
    );
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to save that prescription. Please try again."));
  }
}

export async function downloadClinicalFile(path: string, filename: string): Promise<void> {
  try {
    const response = await api.get<Blob>(path, { responseType: "blob" });
    const blob = response.data;
    const type = (blob.type || "").toLowerCase();
    if (type.includes("json") || type.includes("text")) {
      const text = await blob.text();
      let apiMessage: string | undefined;
      try {
        const parsed = JSON.parse(text) as { error?: { message?: string } };
        apiMessage = parsed.error?.message;
      } catch {

      }
      throw new Error(apiMessage || "Unable to download that file.");
    }
    const header = new Uint8Array(await blob.slice(0, 5).arrayBuffer());
    const looksLikePdf =
      header.length >= 5
      && header[0] === 0x25
      && header[1] === 0x50
      && header[2] === 0x44
      && header[3] === 0x46
      && header[4] === 0x2d; // %PDF-
    if (!looksLikePdf) {
      throw new Error("Downloaded file is not a valid PDF.");
    }
    const url = URL.createObjectURL(new Blob([blob], { type: "application/pdf" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = filename.endsWith(".pdf") ? filename : `${filename}.pdf`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to download that file."));
  }
}
