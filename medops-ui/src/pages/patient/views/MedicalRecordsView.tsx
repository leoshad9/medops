import { useEffect, useState } from "react";
import { Download, FileText, FlaskConical } from "lucide-react";

import { formatClinicDateTime } from "../../../lib/clinicTime";
import {
  downloadClinicalFile,
  listReports,
  type ClinicalReportDto,
} from "../../../services/clinicalService";

function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function MedicalRecordsView() {
  const [reports, setReports] = useState<ClinicalReportDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listReports()
      .then((items) => {
        if (!cancelled) {
          setReports(items);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load medical records.");
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

  const handleDownload = async (report: ClinicalReportDto) => {
    if (!report.hasFile) {
      setError("That record has no downloadable file.");
      return;
    }
    setDownloadingId(report.id);
    setError(null);
    try {
      await downloadClinicalFile(`/v1/reports/${report.id}/file`, report.originalFilename);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Unable to download that file.");
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <div className="space-y-6">
      {error && (
        <p className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {error}
        </p>
      )}

      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="flex items-center justify-between border-b border-brand-line pb-4 mb-4">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-brand-ink">Medical Records & Documents</h2>
              <span className="text-xs font-semibold text-brand-muted bg-brand-paper px-2.5 py-0.5 rounded-full border border-brand-line">
                {loading ? "…" : `${reports.length} files`}
              </span>
            </div>
            <p className="text-xs text-brand-muted mt-0.5">
              Access and download clinical records uploaded by your care team.
            </p>
          </div>
        </div>

        {loading && <p className="text-sm text-brand-muted">Loading medical records…</p>}

        {!loading && reports.length === 0 && (
          <p className="py-8 text-center text-sm text-brand-muted">No medical records yet.</p>
        )}

        {!loading && reports.length > 0 && (
          <div className="divide-y divide-brand-line">
            {reports.map((report) => {
              const isDownloading = downloadingId === report.id;
              const isLab = /lab|blood|patholog/i.test(report.title);

              return (
                <div
                  key={report.id}
                  className="flex items-center justify-between gap-4 py-3.5 transition hover:bg-brand-paper/50 px-2 rounded-xl"
                >
                  <div className="flex items-center gap-3">
                    <span
                      className={`flex h-9 w-9 items-center justify-center rounded-xl shrink-0 ${
                        isLab
                          ? "bg-brand-amber-tint text-brand-amber"
                          : "bg-brand-primary-tint text-brand-primary-dark"
                      }`}
                    >
                      {isLab ? (
                        <FlaskConical className="h-4.5 w-4.5" />
                      ) : (
                        <FileText className="h-4.5 w-4.5" />
                      )}
                    </span>
                    <div>
                      <h3 className="text-sm font-semibold text-brand-ink">{report.title}</h3>
                      <p className="text-xs text-brand-muted mt-0.5">
                        {formatClinicDateTime(report.createdAt)} · {report.doctorName}
                        {report.sizeBytes > 0 ? ` · ${formatFileSize(report.sizeBytes)}` : ""}
                      </p>
                    </div>
                  </div>

                  <button
                    type="button"
                    disabled={!report.hasFile || isDownloading}
                    onClick={() => void handleDownload(report)}
                    className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition cursor-pointer disabled:cursor-not-allowed disabled:opacity-50 ${
                      isDownloading
                        ? "bg-brand-success-tint text-brand-success"
                        : "text-brand-primary-dark hover:bg-brand-primary-tint"
                    }`}
                  >
                    <Download className="h-3.5 w-3.5" />
                    <span>{isDownloading ? "Downloading…" : "Download"}</span>
                  </button>
                </div>
              );
            })}
          </div>
        )}

        {!loading && reports.length > 0 && (
          <p className="mt-4 pt-3 border-t border-brand-line text-xs text-brand-muted text-center">
            Showing {reports.length} clinical record{reports.length === 1 ? "" : "s"} from your care team.
          </p>
        )}
      </section>
    </div>
  );
}
