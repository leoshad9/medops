import { useEffect, useState } from "react";
import { ChevronRight, Download, FileText, FlaskConical, Sparkles } from "lucide-react";

import { formatClinicDateTime } from "../../../lib/clinicTime";
import {
  downloadClinicalFile,
  listReports,
  reviewReport,
  summarizeReport,
  type ClinicalReportDto,
} from "../../../services/clinicalService";

export function LabReportsView() {
  const [reports, setReports] = useState<ClinicalReportDto[]>([]);
  const [selectedReport, setSelectedReport] = useState<ClinicalReportDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [summarizing, setSummarizing] = useState(false);

  const reload = () => listReports().then(setReports);

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
          setError(err instanceof Error ? err.message : "Unable to load reports.");
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

  const newReports = reports.filter((r) => r.status === "NEW");
  const pastReports = reports.filter((r) => r.status === "REVIEWED");

  const markReviewed = async (report: ClinicalReportDto) => {
    try {
      const updated = await reviewReport(report.id);
      setSelectedReport(updated);
      await reload();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Unable to mark as reviewed.");
    }
  };

  const idleLabel = selectedReport?.summary ? "Re-summarize" : "Summarize";
  const summarizeLabel = summarizing ? "Summarizing…" : idleLabel;

  const runSummarize = async (report: ClinicalReportDto) => {
    setSummarizing(true);
    setError(null);
    try {
      const updated = await summarizeReport(report.id);
      setSelectedReport(updated);
      await reload();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Unable to summarize that report.");
    } finally {
      setSummarizing(false);
    }
  };

  return (
    <div className="space-y-6">
      {error && (
        <p className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {error}
        </p>
      )}
      {loading && <p className="text-sm text-brand-muted">Loading reports…</p>}

      {newReports.length > 0 && (
        <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
          <div className="flex items-center justify-between border-b border-brand-line pb-4 mb-4">
            <div>
              <h2 className="text-lg font-bold text-brand-ink">New Results</h2>
              <p className="text-xs text-brand-muted mt-0.5">Reports uploaded by your doctor.</p>
            </div>
            <span className="rounded-full bg-brand-amber-tint px-3 py-1 text-xs font-semibold text-brand-amber">
              {newReports.length} New Results
            </span>
          </div>
          <div className="divide-y divide-brand-line">
            {newReports.map((report) => (
              <div key={report.id} className="flex items-center justify-between gap-4 py-4 px-2">
                <div className="flex items-center gap-3">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-amber-tint text-brand-amber shrink-0">
                    <FlaskConical className="h-4.5 w-4.5" />
                  </span>
                  <div>
                    <h3 className="text-sm font-bold text-brand-ink">{report.title}</h3>
                    <p className="text-xs text-brand-muted mt-0.5">
                      {formatClinicDateTime(report.createdAt)} · {report.doctorName}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedReport(report)}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline"
                >
                  <span>View Report</span>
                  <ChevronRight className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="border-b border-brand-line pb-4 mb-4">
          <h2 className="text-lg font-bold text-brand-ink">Past Reports & History</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-brand-line text-xs font-semibold tracking-wider text-brand-muted uppercase">
                <th className="pb-3 pr-4">Test Report</th>
                <th className="pb-3 pr-4">Date</th>
                <th className="pb-3 pr-4">Uploaded By</th>
                <th className="pb-3 pr-4">Status</th>
                <th className="pb-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-brand-line">
              {!loading && pastReports.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-sm text-brand-muted">
                    No reviewed reports yet.
                  </td>
                </tr>
              ) : (
                pastReports.map((report) => (
                  <tr key={report.id}>
                    <td className="py-4 pr-4 font-semibold text-brand-ink">
                      <div className="flex items-center gap-2">
                        <FileText className="h-4 w-4 text-brand-muted" />
                        <span>{report.title}</span>
                      </div>
                    </td>
                    <td className="py-4 pr-4 font-brand-mono text-xs text-brand-muted">
                      {formatClinicDateTime(report.createdAt)}
                    </td>
                    <td className="py-4 pr-4">{report.doctorName}</td>
                    <td className="py-4 pr-4">
                      <span className="inline-block rounded-full bg-brand-success-tint px-2.5 py-0.5 text-xs font-semibold text-brand-success">
                        Reviewed
                      </span>
                    </td>
                    <td className="py-4 text-right">
                      <button
                        type="button"
                        onClick={() => setSelectedReport(report)}
                        className="font-semibold text-brand-primary-dark hover:underline text-xs"
                      >
                        View Report
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {selectedReport && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-brand-ink/40 p-4">
          <div className="w-full max-w-lg rounded-2xl border border-brand-line bg-white p-6 shadow-xl">
            <h3 className="text-base font-bold text-brand-ink">{selectedReport.title}</h3>
            <p className="text-xs text-brand-muted mt-1">
              {formatClinicDateTime(selectedReport.createdAt)} · {selectedReport.doctorName}
            </p>
            {selectedReport.notes && (
              <p className="mt-4 text-sm text-brand-ink">{selectedReport.notes}</p>
            )}
            {selectedReport.summary ? (
              <div className="mt-4 rounded-xl border border-brand-line bg-brand-canvas/60 p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-brand-muted">
                  Plain-language summary
                </p>
                <p className="mt-2 text-sm text-brand-ink whitespace-pre-wrap">{selectedReport.summary}</p>
                <p className="mt-3 text-xs text-brand-muted">
                  This summary is not a diagnosis or medical advice. Always review the PDF with your care
                  team.
                </p>
              </div>
            ) : (
              <p className="mt-4 text-xs text-brand-muted">
                No summary yet. You can generate a plain-language overview (not a diagnosis).
              </p>
            )}
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              {selectedReport.status === "NEW" && (
                <button
                  type="button"
                  onClick={() => void markReviewed(selectedReport)}
                  className="rounded-lg border border-brand-line px-3 py-1.5 text-xs font-semibold"
                >
                  Mark reviewed
                </button>
              )}
              <button
                type="button"
                disabled={summarizing}
                onClick={() => void runSummarize(selectedReport)}
                className="flex items-center gap-1.5 rounded-lg border border-brand-line px-3 py-1.5 text-xs font-semibold disabled:opacity-60"
              >
                <Sparkles className="h-3.5 w-3.5" />
                {summarizeLabel}
              </button>
              <button
                type="button"
                onClick={() =>
                  void downloadClinicalFile(
                    `/v1/reports/${selectedReport.id}/file`,
                    selectedReport.originalFilename,
                  )
                }
                className="flex items-center gap-1.5 rounded-lg border border-brand-primary px-3 py-1.5 text-xs font-semibold text-brand-primary-dark"
              >
                <Download className="h-3.5 w-3.5" />
                Download PDF
              </button>
              <button
                type="button"
                onClick={() => setSelectedReport(null)}
                className="rounded-lg bg-brand-ink px-4 py-1.5 text-xs font-semibold text-white"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
