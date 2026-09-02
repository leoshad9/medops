import type { ApiResponse } from "../types/api";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

export type InvoiceStatus = "DRAFT" | "ISSUED" | "PARTIALLY_PAID" | "PAID" | "VOID";
export type PaymentMethod = "CASH" | "CARD" | "INSURANCE" | "BANK_TRANSFER";
export type PaymentStatus = "PENDING" | "COMPLETED" | "FAILED" | "REFUNDED";

export interface InvoiceItemDto {
  id: string;
  description: string;
  quantity: number;
  unitPriceCents: number;
  lineTotalCents: number;
}

export interface InvoiceDto {
  id: string;
  patientProfileId: string;
  appointmentId: string | null;
  status: InvoiceStatus;
  totalCents: number;
  paidCents: number;
  balanceCents: number;
  dueDate: string | null;
  notes: string | null;
  items: InvoiceItemDto[];
  createdAt: string;
}

export interface PaymentDto {
  id: string;
  invoiceId: string;
  amountCents: number;
  method: PaymentMethod;
  status: PaymentStatus;
  reference: string | null;
  paidAt: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export async function listInvoices(patientId: string): Promise<PageResponse<InvoiceDto>> {
  try {
    const response = await api.get<ApiResponse<PageResponse<InvoiceDto>>>(
      `/v1/patients/${patientId}/billing/invoices`,
    );
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load invoices. Please try again."));
  }
}

export async function getInvoice(patientId: string, invoiceId: string): Promise<InvoiceDto> {
  try {
    const response = await api.get<ApiResponse<InvoiceDto>>(
      `/v1/patients/${patientId}/billing/invoices/${invoiceId}`,
    );
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load invoice. Please try again."));
  }
}

export async function listPayments(patientId: string, invoiceId: string): Promise<PaymentDto[]> {
  try {
    const response = await api.get<ApiResponse<PaymentDto[]>>(
      `/v1/patients/${patientId}/billing/invoices/${invoiceId}/payments`,
    );
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load payments. Please try again."));
  }
}

export function formatCents(cents: number): string {
  return `₹${(cents / 100).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}
