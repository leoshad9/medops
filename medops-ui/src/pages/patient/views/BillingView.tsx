import { CreditCard, Receipt, ShieldCheck } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";
import type { InvoiceStatus } from "../../../types/patient";

const STATUS_MAP: Record<InvoiceStatus, { label: string; className: string }> = {
  DUE: { label: "Due", className: "bg-brand-rust-tint text-brand-rust" },
  PAID: { label: "Paid", className: "bg-brand-success-tint text-brand-success" },
  WAIVED: { label: "Waived", className: "bg-brand-success-tint text-brand-success" },
};

export function BillingView() {
  const { data } = usePatientPortal();
  const billing = data.billing;
  const handlePay = (invoiceNumber: string, amount: string) => {
    alert(`Redirecting to secure payment gateway for ${invoiceNumber} (${amount})...`);
  };

  const handleReceipt = (invoiceNumber: string, receiptUrl?: string) => {
    const location = receiptUrl ? ` (${receiptUrl})` : "";
    alert(`Opening receipt for ${invoiceNumber}${location}...`);
  };

  return (
    <div className="space-y-6">
      {/* 3 Summary Stat Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-muted">Outstanding Balance</span>
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-rust-tint text-brand-rust">
              <CreditCard className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-3 font-brand-mono text-2xl font-bold text-brand-rust">
            {billing.outstandingBalance}
          </div>
          <p className="mt-0.5 text-xs text-brand-muted">{billing.dueInvoiceCount} invoice due</p>
        </div>

        <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-muted">Paid This Year</span>
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-primary-tint text-brand-primary-dark">
              <Receipt className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-3 font-brand-mono text-2xl font-bold text-brand-ink">
            {billing.paidThisYear}
          </div>
          <p className="mt-0.5 text-xs text-brand-muted">{billing.paidInvoiceCount} invoices settled</p>
        </div>

        <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-muted">Insurance Coverage</span>
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-primary-tint text-brand-primary-dark">
              <ShieldCheck className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-3 text-lg font-bold text-brand-ink">
            {billing.insuranceProvider}
          </div>
          <p className="mt-0.5 text-xs font-semibold text-brand-success">{billing.policyStatus}</p>
        </div>
      </div>

      {/* Invoices Table */}
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="border-b border-brand-line pb-4 mb-4">
          <h2 className="text-lg font-bold text-brand-ink">Invoices & Statements</h2>
          <p className="text-xs text-brand-muted mt-0.5">
            Detailed itemized breakdown of clinical appointments, procedures, and payments.
          </p>
        </div>

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
              {billing.invoices.map((inv) => {
                const statusInfo = STATUS_MAP[inv.status];
                return (
                  <tr key={inv.id} className="transition hover:bg-brand-paper/50">
                    <td className="py-4 pr-4 font-brand-mono text-xs font-bold text-brand-ink">
                      {inv.invoiceNumber}
                    </td>
                    <td className="py-4 pr-4 font-brand-mono text-xs text-brand-muted">
                      {inv.date}
                    </td>
                    <td className="py-4 pr-4 font-medium text-brand-ink">
                      {inv.description}
                    </td>
                    <td className="py-4 pr-4 font-brand-mono font-semibold text-brand-ink">
                      {inv.amount}
                    </td>
                    <td className="py-4 pr-4">
                      <span
                        className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusInfo.className}`}
                      >
                        {statusInfo.label}
                      </span>
                    </td>
                    <td className="py-4 text-right">
                      {inv.status === "DUE" ? (
                        <button
                          type="button"
                          onClick={() => handlePay(inv.invoiceNumber, inv.amount)}
                          className="rounded-lg bg-brand-rust px-3 py-1 text-xs font-semibold text-white transition hover:bg-red-700 cursor-pointer shadow-2xs"
                        >
                          Pay Now
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => handleReceipt(inv.invoiceNumber, inv.receiptUrl)}
                          className="font-semibold text-brand-primary-dark hover:underline cursor-pointer text-xs"
                        >
                          Receipt
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
