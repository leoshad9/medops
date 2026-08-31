import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { doctorPatientChartPath } from "../../lib/doctorRoutes";
import { formatClinicDateTime } from "../../lib/clinicTime";
import { listPrescriptions, type PrescriptionDto } from "../../services/clinicalService";

export function DoctorPrescriptionsView() {
  const [items, setItems] = useState<PrescriptionDto[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listPrescriptions()
      .then((result) => {
        if (!cancelled) {
          setItems(result);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load prescriptions.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <section className="rounded-xl border border-brand-line bg-white p-6 shadow-xs">
      {error && (
        <p className="mb-4 rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {error}
        </p>
      )}
      {items.length === 0 ? (
        <p className="text-sm text-brand-muted">No prescriptions yet. Open a patient chart to write one.</p>
      ) : (
        <ul className="divide-y divide-brand-line text-sm">
          {items.map((rx) => (
            <li key={rx.id} className="flex items-center justify-between gap-3 py-3">
              <div>
                <p className="font-semibold text-brand-ink">
                  {rx.medicationName} · {rx.dosage}
                </p>
                <p className="text-xs text-brand-muted">
                  {rx.patientName} ({rx.patientMrn}) · {formatClinicDateTime(rx.createdAt)}
                </p>
              </div>
              <Link
                to={doctorPatientChartPath(rx.patientId)}
                className="text-xs font-semibold text-brand-primary-dark hover:underline"
              >
                Chart
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
