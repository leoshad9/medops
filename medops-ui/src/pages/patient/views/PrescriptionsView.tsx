import { useState } from "react";
import { Check, Pill, RefreshCw } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";

const REQUESTED_BUTTON_STYLE =
  "bg-brand-success-tint text-brand-success border border-brand-success/30";
const RENEWAL_BUTTON_STYLE = "bg-brand-primary text-white hover:bg-brand-primary-dark shadow-2xs";
const REFILL_BUTTON_STYLE =
  "border border-brand-primary text-brand-primary-dark hover:bg-brand-primary-tint bg-white";

function refillButtonStyle(isActionDone: boolean, isRenewalNeeded: boolean): string {
  if (isActionDone) {
    return REQUESTED_BUTTON_STYLE;
  }
  return isRenewalNeeded ? RENEWAL_BUTTON_STYLE : REFILL_BUTTON_STYLE;
}

function refillButtonLabel(isActionDone: boolean, isRenewalNeeded: boolean): string {
  if (isActionDone) {
    return "Request Submitted";
  }
  return isRenewalNeeded ? "Request Renewal" : "Request Refill";
}

export function PrescriptionsView() {
  const { data } = usePatientPortal();
  const prescriptions = data.prescriptions;
  const [requestedId, setRequestedId] = useState<string | null>(null);

  const handleAction = (id: string, name: string) => {
    setRequestedId(id);
    setTimeout(() => {
      alert(`Refill/Renewal request for ${name} submitted to your prescribing physician.`);
    }, 150);
  };

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="border-b border-brand-line pb-4 mb-6 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-brand-ink">Active & Recent Prescriptions</h2>
            <p className="text-xs text-brand-muted mt-0.5">
              Manage your medications, check refill balances, and request prescription renewals.
            </p>
          </div>
          <div className="flex items-center gap-2 text-xs font-semibold text-brand-primary-dark bg-brand-primary-tint px-3 py-1.5 rounded-full">
            <Pill className="h-3.5 w-3.5" />
            <span>{prescriptions.filter((p) => p.status === "ACTIVE").length} Active Medications</span>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
          {prescriptions.map((rx) => {
            const isRenewalNeeded = rx.status === "RENEWAL_NEEDED";
            const isActionDone = requestedId === rx.id;

            return (
              <div
                key={rx.id}
                className="flex flex-col justify-between rounded-2xl border border-brand-line bg-white p-5 shadow-xs transition hover:border-brand-primary/50 hover:shadow-sm"
              >
                <div>
                  <div className="flex items-start justify-between">
                    <span
                      className={`flex h-9 w-9 items-center justify-center rounded-xl ${
                        isRenewalNeeded
                          ? "bg-brand-amber-tint text-brand-amber"
                          : "bg-brand-primary-tint text-brand-primary-dark"
                      }`}
                    >
                      <Pill className="h-4.5 w-4.5" />
                    </span>
                    <span
                      className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                        isRenewalNeeded
                          ? "bg-brand-amber-tint text-brand-amber"
                          : "bg-brand-success-tint text-brand-success"
                      }`}
                    >
                      {isRenewalNeeded ? "Renewal needed" : "Active"}
                    </span>
                  </div>

                  <h3 className="mt-3 text-base font-bold text-brand-ink">{rx.medicationName}</h3>
                  <p className="font-brand-mono text-xs font-semibold text-brand-muted mt-0.5">
                    {rx.dosage}
                  </p>
                  <p className="mt-1 text-xs text-brand-muted leading-relaxed">
                    {rx.instructions}
                  </p>

                  <div className="mt-4 pt-3 border-t border-brand-line space-y-1 text-xs text-brand-muted">
                    <div className="flex justify-between">
                      <span>Prescribed by:</span>
                      <span className="font-semibold text-brand-ink">{rx.prescribedBy}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Refills remaining:</span>
                      <span className="font-brand-mono font-bold text-brand-ink">
                        {rx.refillsRemaining}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="mt-5 pt-2">
                  <button
                    type="button"
                    onClick={() => handleAction(rx.id, rx.medicationName)}
                    className={`flex w-full items-center justify-center gap-2 rounded-xl py-2.5 text-xs font-semibold transition cursor-pointer ${refillButtonStyle(
                      isActionDone,
                      isRenewalNeeded,
                    )}`}
                  >
                    {isActionDone ? (
                      <Check className="h-3.5 w-3.5" />
                    ) : (
                      <RefreshCw className="h-3.5 w-3.5" />
                    )}
                    <span>{refillButtonLabel(isActionDone, isRenewalNeeded)}</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}
