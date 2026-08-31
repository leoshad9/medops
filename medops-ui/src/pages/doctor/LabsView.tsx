import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import { doctorPatientChartPath } from "../../lib/doctorRoutes";
import { formatClinicDateTime } from "../../lib/clinicTime";
import { downloadClinicalFile, listReports, type ClinicalReportDto } from "../../services/clinicalService";

export function DoctorLabsView() {
  const [items, setItems] = useState<ClinicalReportDto[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listReports()
      .then((result) => {
        if (!cancelled) {
          setItems(result);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load reports.");
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
        <p className="text-sm text-brand-muted">No reports yet. Open a patient chart to upload a PDF.</p>
      ) : (
        <ul className="divide-y divide-brand-line text-sm">
          {items.map((report) => (
            <li key={report.id} className="flex items-center justify-between gap-3 py-3">
              <div>
                <p className="font-semibold text-brand-ink">{report.title}</p>
                <p className="text-xs text-brand-muted">
                  {report.patientName} ({report.patientMrn}) · {formatClinicDateTime(report.createdAt)} · {report.status}
                </p>
                {report.summary && (
                  <p className="mt-1 text-xs text-brand-ink line-clamp-2">{report.summary}</p>
                )}
              </div>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  className="text-xs font-semibold text-brand-primary-dark hover:underline"
                  onClick={() =>
                    void downloadClinicalFile(`/v1/reports/${report.id}/file`, report.originalFilename)
                  }
                >
                  Download
                </button>
                <Link
                  to={doctorPatientChartPath(report.patientId)}
                  className="text-xs font-semibold text-brand-primary-dark hover:underline"
                >
                  Chart
                </Link>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
