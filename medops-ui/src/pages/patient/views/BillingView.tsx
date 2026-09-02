import { useEffect, useMemo, useState } from "react";
import { CreditCard, Receipt, ShieldCheck } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";
import { formatClinicDateTime } from "../../../lib/clinicTime";
import {
  formatCents,
  listInvoices,
  type InvoiceDto,
  type InvoiceStatus,
} from "../../../services/billingService";

const STATUS_MAP: Record<InvoiceStatus, { label: string; className: string }> = {
  DRAFT: { label: "Draft", className: "bg-brand-paper text-brand-muted" },
  ISSUED: { label: "Due", className: "bg-brand-rust-tint text-brand-rust" },
  PARTIALLY_PAID: { label: "Partial", className: "bg-brand-amber-tint text-brand-amber" },
  PAID: { label: "Paid", className: "bg-brand-success-tint text-brand-success" },
  VOID: { label: "Void", className: "bg-brand-paper text-brand-muted" },
};

const PAYABLE: InvoiceStatus[] = ["ISSUED", "PARTIALLY_PAID"];

function invoiceDescription(invoice: InvoiceDto): string {
  if (invoice.notes?.trim()) {
    return invoice.notes.trim();
  }
  if (invoice.items.length === 0) {
    return "Clinical services";
  }
  if (invoice.items.length === 1) {
    return invoice.items[0].description;
  }
  return `${invoice.items[0].description} (+${invoice.items.length - 1} more)`;
}

function shortInvoiceNumber(id: string): string {
  return `INV-${id.slice(0, 8).toUpperCase()}`;
}

export function BillingView() {
  const { patientId } = usePatientPortal();
  const [invoices, setInvoices] = useState<InvoiceDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!patientId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    listInvoices(patientId)
      .then((page) => {
        if (!cancelled) {
          setInvoices(page.content);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load invoices.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [patientId]);

  const summary = useMemo(() => {
    const outstanding = invoices
      .filter((inv) => PAYABLE.includes(inv.status) && inv.balanceCents > 0)
      .reduce((sum, inv) => sum + inv.balanceCents, 0);
    const dueCount = invoices.filter(
      (inv) => PAYABLE.includes(inv.status) && inv.balanceCents > 0,
    ).length;
    const year = new Date().getFullYear();
    const paidThisYear = invoices.filter((inv) => {
      if (inv.status !== "PAID" && inv.paidCents <= 0) {
        return false;
      }
      return new Date(inv.createdAt).getFullYear() === year && inv.paidCents > 0;
    });
    const paidCents = paidThisYear.reduce((sum, inv) => sum + inv.paidCents, 0);
    return {
      outstandingBalance: formatCents(outstanding),
      dueInvoiceCount: dueCount,
      paidThisYear: formatCents(paidCents),
      paidInvoiceCount: paidThisYear.length,
    };
  }, [invoices]);

  const handlePay = (invoice: InvoiceDto) => {
    alert(
      `Online payment is not connected yet. Balance due for ${shortInvoiceNumber(invoice.id)}: ${formatCents(invoice.balanceCents)}. Please pay at the clinic desk.`,
    );
  };

  const handleReceipt = (invoice: InvoiceDto) => {
    alert(`Receipt download is not available yet for ${shortInvoiceNumber(invoice.id)}.`);
  };

  return (
    <div className="space-y-6">
      {error && (
        <p className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {error}
        </p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-muted">Outstanding Balance</span>
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-rust-tint text-brand-rust">
              <CreditCard className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-3 font-brand-mono text-2xl font-bold text-brand-rust">
            {loading ? "…" : summary.outstandingBalance}
          </div>
          <p className="mt-0.5 text-xs text-brand-muted">
            {loading ? "Loading…" : `${summary.dueInvoiceCount} invoice${summary.dueInvoiceCount === 1 ? "" : "s"} due`}
          </p>
        </div>

        <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-muted">Paid This Year</span>
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-primary-tint text-brand-primary-dark">
              <Receipt className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-3 font-brand-mono text-2xl font-bold text-brand-ink">
            {loading ? "…" : summary.paidThisYear}
          </div>
          <p className="mt-0.5 text-xs text-brand-muted">
            {loading ? "Loading…" : `${summary.paidInvoiceCount} invoice${summary.paidInvoiceCount === 1 ? "" : "s"} settled`}
          </p>
        </div>

        <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-muted">Insurance Coverage</span>
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-primary-tint text-brand-primary-dark">
              <ShieldCheck className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-3 text-lg font-bold text-brand-ink">Not on file</div>
          <p className="mt-0.5 text-xs text-brand-muted">Insurance details are not connected yet</p>
        </div>
      </div>

      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="border-b border-brand-line pb-4 mb-4">
          <h2 className="text-lg font-bold text-brand-ink">Invoices & Statements</h2>
          <p className="text-xs text-brand-muted mt-0.5">
            Itemized invoices for clinical appointments, procedures, and payments.
          </p>
        </div>

        {!patientId && !loading && (
          <p className="py-8 text-center text-sm text-brand-muted">
            Sign in as a patient to view your invoices.
          </p>
        )}

        {loading && <p className="text-sm text-brand-muted">Loading invoices…</p>}

        {!loading && patientId && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-brand-line text-xs font-semibold tracking-wider text-brand-muted uppercase">
                  <th className="pb-3 pr-4">Invoice #</th>
                  <th className="pb-3 pr-4">Date</th>
                  <th className="pb-3 pr-4">Description</th>
                  <th className="pb-3 pr-4">Amount</th>
                  <th className="pb-3 pr-4">Status</th>
                  <th className="pb-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-brand-line">
                {invoices.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-sm text-brand-muted">
                      No invoices yet.
                    </td>
                  </tr>
                ) : (
                  invoices.map((inv) => {
                    const statusInfo = STATUS_MAP[inv.status];
                    const canPay = PAYABLE.includes(inv.status) && inv.balanceCents > 0;
                    return (
                      <tr key={inv.id} className="transition hover:bg-brand-paper/50">
                        <td className="py-4 pr-4 font-brand-mono text-xs font-bold text-brand-ink">
                          {shortInvoiceNumber(inv.id)}
                        </td>
                        <td className="py-4 pr-4 font-brand-mono text-xs text-brand-muted">
                          {formatClinicDateTime(inv.createdAt)}
                        </td>
                        <td className="py-4 pr-4 font-medium text-brand-ink">
                          {invoiceDescription(inv)}
                        </td>
                        <td className="py-4 pr-4 font-brand-mono font-semibold text-brand-ink">
                          {formatCents(inv.totalCents)}
                        </td>
                        <td className="py-4 pr-4">
                          <span
                            className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusInfo.className}`}
                          >
                            {statusInfo.label}
                          </span>
                        </td>
                        <td className="py-4 text-right">
                          {canPay ? (
                            <button
                              type="button"
                              onClick={() => handlePay(inv)}
                              className="rounded-lg bg-brand-rust px-3 py-1 text-xs font-semibold text-white transition hover:bg-red-700 cursor-pointer shadow-2xs"
                            >
                              Pay Now
                            </button>
                          ) : inv.status === "PAID" ? (
                            <button
                              type="button"
                              onClick={() => handleReceipt(inv)}
                              className="font-semibold text-brand-primary-dark hover:underline cursor-pointer text-xs"
                            >
                              Receipt
                            </button>
                          ) : (
                            <span className="text-xs text-brand-muted">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
