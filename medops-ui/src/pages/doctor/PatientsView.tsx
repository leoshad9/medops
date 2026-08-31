import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { doctorPatientChartPath } from "../../lib/doctorRoutes";
import { listMyPatients, type DoctorPatientSummary } from "../../services/clinicalService";

export function DoctorPatientsView() {
  const [patients, setPatients] = useState<DoctorPatientSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    listMyPatients()
      .then((items) => {
        if (!cancelled) {
          setPatients(items);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load patients.");
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

  return (
    <section className="rounded-xl border border-brand-line bg-white p-6 shadow-xs">
      {error && (
        <p className="mb-4 rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {error}
        </p>
      )}
      {loading ? (
        <p className="text-sm text-brand-muted">Loading patients…</p>
      ) : patients.length === 0 ? (
        <p className="text-sm text-brand-muted">
          No patients yet. Complete or book a visit with a patient to attach reports and prescriptions.
        </p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-brand-line text-xs font-semibold uppercase tracking-wider text-brand-muted">
              <th className="pb-3">Patient</th>
              <th className="pb-3">MRN</th>
              <th className="pb-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-brand-line">
            {patients.map((patient) => (
              <tr key={patient.id}>
                <td className="py-3 font-semibold text-brand-ink">{patient.fullName}</td>
                <td className="py-3 font-mono text-xs text-brand-muted">{patient.mrn}</td>
                <td className="py-3 text-right">
                  <Link
                    to={doctorPatientChartPath(patient.id)}
                    className="text-xs font-semibold text-brand-primary-dark hover:underline"
                  >
                    Open chart
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
