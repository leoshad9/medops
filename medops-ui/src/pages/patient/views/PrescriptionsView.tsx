import { useEffect, useState } from "react";
import { Check, Pill } from "lucide-react";

import { formatClinicDateTime } from "../../../lib/clinicTime";
import { listPrescriptions, type PrescriptionDto } from "../../../services/clinicalService";

export function PrescriptionsView() {
  const [prescriptions, setPrescriptions] = useState<PrescriptionDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    listPrescriptions()
      .then((items) => {
        if (!cancelled) {
          setPrescriptions(items);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load prescriptions.");
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
  }, []);

  const renderContent = () => {
    if (loading) return <p className="text-sm text-brand-muted">Loading prescriptions…</p>;
    if (prescriptions.length === 0) return <p className="text-sm text-brand-muted">No prescriptions yet.</p>;
    return (
      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
        {prescriptions.map((rx) => (
          <div
            key={rx.id}
            className="flex flex-col justify-between rounded-2xl border border-brand-line bg-white p-5 shadow-xs"
          >
            <div>
              <div className="flex items-start justify-between">
                <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary-tint text-brand-primary-dark">
                  <Pill className="h-4.5 w-4.5" />
                </span>
                <span className="rounded-full bg-brand-success-tint px-2.5 py-0.5 text-xs font-semibold text-brand-success">
                  {rx.status === "ACTIVE" ? "Active" : "Discontinued"}
                </span>
              </div>
              <h3 className="mt-3 text-base font-bold text-brand-ink">{rx.medicationName}</h3>
              <p className="font-brand-mono text-xs font-semibold text-brand-muted mt-0.5">{rx.dosage}</p>
              <p className="mt-1 text-xs text-brand-muted leading-relaxed">{rx.instructions}</p>
              <div className="mt-4 pt-3 border-t border-brand-line space-y-1 text-xs text-brand-muted">
                <div className="flex justify-between">
                  <span>Prescribed by:</span>
                  <span className="font-semibold text-brand-ink">{rx.doctorName}</span>
                </div>
                <div className="flex justify-between">
                  <span>Date:</span>
                  <span className="font-brand-mono text-brand-ink">{formatClinicDateTime(rx.createdAt)}</span>
                </div>
                <div className="flex justify-between">
                  <span>Refills remaining:</span>
                  <span className="font-brand-mono font-bold text-brand-ink">{rx.refillsRemaining}</span>
                </div>
              </div>
            </div>
            <div className="mt-5 pt-2">
              <span className="flex w-full items-center justify-center gap-2 rounded-xl py-2.5 text-xs font-semibold bg-brand-paper text-brand-muted">
                <Check className="h-3.5 w-3.5" />
                On file
              </span>
            </div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="border-b border-brand-line pb-4 mb-6 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-brand-ink">Active & Recent Prescriptions</h2>
            <p className="text-xs text-brand-muted mt-0.5">
              Medications prescribed to you by your doctors.
            </p>
          </div>
          <div className="flex items-center gap-2 text-xs font-semibold text-brand-primary-dark bg-brand-primary-tint px-3 py-1.5 rounded-full">
            <Pill className="h-3.5 w-3.5" />
            <span>
              {prescriptions.filter((p) => p.status === "ACTIVE").length} Active Medications
            </span>
          </div>
        </div>

        {error && (
          <p className="mb-4 rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
            {error}
          </p>
        )}
        {renderContent()}
      </section>
    </div>
  );
}
