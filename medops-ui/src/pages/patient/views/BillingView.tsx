import { useCallback, useEffect, useMemo, useState } from "react";
import { CheckCircle2, CreditCard, Loader2, Receipt, ShieldCheck, X } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";
import { formatClinicDateTime } from "../../../lib/clinicTime";
import {
  createStripeCheckoutSession,
  formatCents,
  listInvoices,
  type InvoiceDto,
  type InvoiceStatus,
  type StripeCheckoutSessionResponse,
} from "../../../services/billingService";
import { loadStripe, type Stripe } from "@stripe/stripe-js";
import {
  CheckoutElementsProvider,
  PaymentElement,
  useCheckoutElements,
} from "@stripe/react-stripe-js/checkout";

/** Lifecycle wording for the insurance coverage card. */
type InsuranceLifecycle =
  | "not provided"
  | "pending verification"
  | "verified"
  | "coverage inactive";

/**
 * Derives the patient-facing insurance lifecycle from the profile fields.
 * The profile is the single source of truth; this view no longer hardcodes
 * "Not on file" and instead reflects what is actually stored.
 */
function insuranceLifecycle(
  provider: string | undefined,
  policyNumber: string | undefined,
): { status: InsuranceLifecycle; detail: string } {
  if (!provider && !policyNumber) {
    return { status: "not provided", detail: "Insurance details are not connected yet" };
  }
  if (!policyNumber) {
    return {
      status: "pending verification",
      detail: `${provider} on file � policy number missing, verification pending`,
    };
  }
  return {
    status: "verified",
    detail: `${provider} � Policy ${policyNumber}`,
  };
}

const STATUS_MAP: Record<InvoiceStatus, { label: string; className: string }> = {
  DRAFT: { label: "Draft", className: "bg-brand-paper text-brand-muted" },
  ISSUED: { label: "Due", className: "bg-brand-rust-tint text-brand-rust" },
  PARTIALLY_PAID: { label: "Partial", className: "bg-brand-amber-tint text-brand-amber" },
  PAID: { label: "Paid", className: "bg-brand-success-tint text-brand-success" },
  VOID: { label: "Void", className: "bg-brand-paper text-brand-muted" },
};

const PAYABLE: InvoiceStatus[] = ["ISSUED", "PARTIALLY_PAID"];

/** Calendar year for "paid this year" rollups; stable for the app session. */
const CURRENT_YEAR = new Date().getFullYear();
const EMPTY_INVOICES: InvoiceDto[] = [];

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

/** Renders the selected patient's invoices and payment controls. */
export function BillingView() {
  const { patientId, profile } = usePatientPortal();
  const [patientQuery, setPatientQuery] = useState<{
    patientId: string;
    invoices: InvoiceDto[];
    error: string | null;
  } | null>(null);
  const currentQuery = patientQuery?.patientId === patientId ? patientQuery : null;
  const invoices = currentQuery?.invoices ?? EMPTY_INVOICES;
  const error = currentQuery?.error ?? null;
  const loading = patientId !== null && currentQuery === null;

  useEffect(() => {
    if (!patientId) {
      return;
    }
    let cancelled = false;
    listInvoices(patientId)
      .then((page) => {
        if (!cancelled) {
          setPatientQuery({ patientId, invoices: page.content, error: null });
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setPatientQuery({
            patientId,
            invoices: [],
            error: err instanceof Error ? err.message : "Unable to load invoices.",
          });
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
    const year = CURRENT_YEAR;
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
      insurance: insuranceLifecycle(profile?.insuranceProvider, profile?.insurancePolicyNumber),
    };
  }, [invoices, profile?.insuranceProvider, profile?.insurancePolicyNumber]);

  const handleReceipt = (invoice: InvoiceDto) => {
    alert(`Receipt download is not available yet for ${shortInvoiceNumber(invoice.id)}.`);
  };

  // Stripe checkout state
  const [checkoutSession, setCheckoutSession] = useState<StripeCheckoutSessionResponse | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [payingInvoiceId, setPayingInvoiceId] = useState<string | null>(null);
  const [paymentStatus, setPaymentStatus] = useState<"idle" | "succeeded">(() =>
    // Redirect-based methods (3DS/UPI) navigate to returnUrl?session_id=… as a
    // full page load, so derive the initial state from the URL during render
    // instead of setting it in an effect.
    new URLSearchParams(window.location.search).get("session_id") ? "succeeded" : "idle",
  );

  // Build the Stripe.js instance from the backend-provided publishable key only.
  const stripePromise = useMemo<Promise<Stripe | null>>(() => {
    const key =
      checkoutSession?.publishableKey?.trim() ||
      import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY?.trim() ||
      "";
    if (!key) return Promise.resolve(null);
    return loadStripe(key);
  }, [checkoutSession?.publishableKey]);

  const refreshInvoices = useCallback(() => {
    if (!patientId) return;
    // Use .then()/.catch() continuations (same shape as the initial-load effect
    // below) so the compiler treats setPatientQuery as an async continuation
    // rather than a synchronous setState-in-effect when called from the
    // redirect-return effect.
    listInvoices(patientId)
      .then((page) => {
        setPatientQuery({ patientId, invoices: page.content, error: null });
      })
      .catch(() => {
        // Keep stale list; the modal already closed on success.
      });
  }, [patientId]);

  // After a redirect-based method (3DS, UPI) returns to returnUrl, refresh so a
  // webhook-settled invoice flips to PAID without manual reload. paymentStatus is
  // already "succeeded" from the useState initializer; here we only refetch and
  // strip the session_id param from the address bar.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("session_id")) {
      refreshInvoices();
      params.delete("session_id");
      const clean = `${window.location.pathname}${params.toString() ? `?${params.toString()}` : ""}`;
      window.history.replaceState({}, "", clean);
    }
  }, [refreshInvoices]);

  const closePaymentModal = useCallback(() => {
    setCheckoutSession(null);
    setCheckoutError(null);
    // Refresh in case the webhook settled while the modal was open.
    refreshInvoices();
  }, [refreshInvoices]);

  const handlePay = useCallback(
    async (invoice: InvoiceDto) => {
      if (!patientId || payingInvoiceId !== null) return;
      setPayingInvoiceId(invoice.id);
      setCheckoutError(null);
      setPaymentStatus("idle");
      try {
        const session = await createStripeCheckoutSession(patientId, invoice.id);
        if (!session.clientSecret) {
          throw new Error("Payment session is missing a client secret. Please try again.");
        }
        setCheckoutSession(session);
      } catch (err: unknown) {
        setCheckoutError(err instanceof Error ? err.message : "Failed to initialize payment");
      } finally {
        setPayingInvoiceId(null);
      }
    },
    [patientId, payingInvoiceId],
  );

  return (
    <div className="space-y-6">
      {error && (
        <p className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {error}
        </p>
      )}
      {checkoutError && !checkoutSession && (
        <p className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {checkoutError}
        </p>
      )}
      {paymentStatus === "succeeded" && (
        <p className="flex items-center gap-2 rounded-xl border border-brand-success/30 bg-brand-success-tint px-4 py-3 text-sm text-brand-success">
          <CheckCircle2 className="h-4 w-4" /> Payment received — refreshing invoices…
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
            {loading ? "�" : summary.outstandingBalance}
          </div>
          <p className="mt-0.5 text-xs text-brand-muted">
            {loading ? "Loading�" : `${summary.dueInvoiceCount} invoice${summary.dueInvoiceCount === 1 ? "" : "s"} due`}
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
            {loading ? "�" : summary.paidThisYear}
          </div>
          <p className="mt-0.5 text-xs text-brand-muted">
            {loading ? "Loading�" : `${summary.paidInvoiceCount} invoice${summary.paidInvoiceCount === 1 ? "" : "s"} settled`}
          </p>
        </div>

        <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-brand-muted">Insurance Coverage</span>
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-primary-tint text-brand-primary-dark">
              <ShieldCheck className="h-4 w-4" />
            </span>
          </div>
          <div className="mt-3 text-lg font-bold text-brand-ink">{summary.insurance.status}</div>
          <p className="mt-0.5 text-xs text-brand-muted">{summary.insurance.detail}</p>
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

        {loading && <p className="text-sm text-brand-muted">Loading invoices�</p>}

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
                              onClick={() => void handlePay(inv)}
                              disabled={payingInvoiceId === inv.id}
                              className="rounded-lg bg-brand-rust px-3 py-1 text-xs font-semibold text-white transition hover:bg-red-700 cursor-pointer shadow-2xs disabled:opacity-50"
                            >
                              {payingInvoiceId === inv.id ? "Starting…" : "Pay Now"}
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

      {checkoutSession && (
        <CheckoutPaymentModal
          stripePromise={stripePromise}
          session={checkoutSession}
          error={checkoutError}
          onClose={closePaymentModal}
          onSucceeded={() => {
            setCheckoutSession(null);
            setPaymentStatus("succeeded");
            refreshInvoices();
          }}
          onError={setCheckoutError}
        />
      )}
    </div>
  );
}

function CheckoutPaymentModal({
  stripePromise,
  session,
  error,
  onClose,
  onSucceeded,
  onError,
}: {
  stripePromise: Promise<Stripe | null>;
  session: StripeCheckoutSessionResponse;
  error: string | null;
  onClose: () => void;
  onSucceeded: () => void;
  onError: (message: string | null) => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-brand-ink">Complete Payment</h3>
          <button
            type="button"
            onClick={onClose}
            className="text-brand-muted hover:text-brand-ink transition"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <p className="mb-4 font-brand-mono text-2xl font-bold text-brand-ink">
          {formatCents(session.amountCents, session.currency)}
        </p>

        {error && (
          <div className="mb-4 rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
            {error}
          </div>
        )}

        <CheckoutElementsProvider
          stripe={stripePromise}
          options={{ clientSecret: session.clientSecret }}
          key={session.sessionId}
        >
          <CheckoutForm
            session={session}
            onClose={onClose}
            onSucceeded={onSucceeded}
            onError={onError}
          />
        </CheckoutElementsProvider>
      </div>
    </div>
  );
}

function CheckoutForm({
  session,
  onClose,
  onSucceeded,
  onError,
}: {
  session: StripeCheckoutSessionResponse;
  onClose: () => void;
  onSucceeded: () => void;
  onError: (message: string | null) => void;
}) {
  const result = useCheckoutElements();
  const [submitting, setSubmitting] = useState(false);

  if (result.type === "loading") {
    return (
      <div className="flex items-center justify-center gap-2 py-8 text-sm text-brand-muted">
        <Loader2 className="h-5 w-5 animate-spin" /> Loading secure payment form…
      </div>
    );
  }

  if (result.type === "error") {
    return (
      <div className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
        {result.error.message || "Unable to load the payment form. Please try again."}
      </div>
    );
  }

  const { checkout } = result;

  const handlePay = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    onError(null);
    try {
      const confirmResult = await checkout.confirm({ redirect: "if_required" });
      if (confirmResult.type === "error") {
        onError(confirmResult.error.message || "Payment failed. Please try again.");
        return;
      }
      const order = confirmResult.session;
      if (order.status.type === "complete" && order.status.paymentStatus === "paid") {
        onSucceeded();
      } else {
        // Redirect-based method (3DS/UPI): Stripe navigates to returnUrl,
        // which refreshes invoices on return.
      }
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : "Payment failed. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={(event) => void handlePay(event)} className="space-y-4">
      <PaymentElement />
      <p className="flex items-center gap-1.5 text-xs text-brand-muted">
        <ShieldCheck className="h-3.5 w-3.5" /> Secured by Stripe
      </p>
      <div className="flex gap-3">
        <button
          type="button"
          onClick={onClose}
          disabled={submitting}
          className="flex-1 rounded-xl border border-brand-line px-4 py-2.5 text-sm font-semibold text-brand-ink transition hover:bg-brand-paper disabled:opacity-50"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={submitting || !checkout.canConfirm}
          className="flex-1 rounded-xl bg-brand-rust px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-red-700 disabled:opacity-50"
        >
          {submitting ? (
            <span className="flex items-center justify-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin" /> Processing…
            </span>
          ) : (
            `Pay ${formatCents(session.amountCents, session.currency)}`
          )}
        </button>
      </div>
    </form>
  );
}
