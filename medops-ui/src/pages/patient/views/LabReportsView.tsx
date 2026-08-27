import { useState } from "react";
import { ChevronRight, Download, FileText, FlaskConical } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";
import type { LabReportItem } from "../../../types/patient";

export function LabReportsView() {
  const { data } = usePatientPortal();
  const labReports = data.labReports;
  const [selectedReport, setSelectedReport] = useState<LabReportItem | null>(null);

  const newReports = labReports.filter((r) => r.status === "NEW");
  const pastReports = labReports.filter((r) => r.status === "REVIEWED");

  return (
    <div className="space-y-6">
      {/* New Results Section */}
      {newReports.length > 0 && (
        <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
          <div className="flex items-center justify-between border-b border-brand-line pb-4 mb-4">
            <div>
              <h2 className="text-lg font-bold text-brand-ink">New Results</h2>
              <p className="text-xs text-brand-muted mt-0.5">
                Recently completed diagnostics requiring your attention or doctor follow-up.
              </p>
            </div>
            <span className="rounded-full bg-brand-amber-tint px-3 py-1 text-xs font-semibold text-brand-amber">
              {newReports.length} New Results
            </span>
          </div>

          <div className="divide-y divide-brand-line">
            {newReports.map((report) => (
              <div
                key={report.id}
                className="flex items-center justify-between gap-4 py-4 transition hover:bg-brand-paper/40 px-2 rounded-xl"
              >
                <div className="flex items-center gap-3">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-amber-tint text-brand-amber shrink-0">
                    <FlaskConical className="h-4.5 w-4.5" />
                  </span>
                  <div>
                    <h3 className="text-sm font-bold text-brand-ink">{report.title}</h3>
                    <p className="text-xs text-brand-muted mt-0.5">
                      {report.date} · Ordered by {report.orderedBy}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  <span className="rounded-full bg-brand-amber-tint px-2.5 py-0.5 text-xs font-semibold text-brand-amber">
                    New
                  </span>
                  <button
                    type="button"
                    onClick={() => setSelectedReport(report)}
                    className="inline-flex items-center gap-1 text-xs font-semibold text-brand-primary-dark hover:underline cursor-pointer"
                  >
                    <span>View Report</span>
                    <ChevronRight className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Past Reports Table */}
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="border-b border-brand-line pb-4 mb-4">
          <h2 className="text-lg font-bold text-brand-ink">Past Reports & History</h2>
          <p className="text-xs text-brand-muted mt-0.5">
            Archived laboratory tests and historical diagnostic findings.
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-brand-line text-xs font-semibold tracking-wider text-brand-muted uppercase">
                <th className="pb-3 pr-4">Test Report</th>
                <th className="pb-3 pr-4">Date</th>
                <th className="pb-3 pr-4">Ordered By</th>
                <th className="pb-3 pr-4">Status</th>
                <th className="pb-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-brand-line">
              {pastReports.map((report) => (
                <tr key={report.id} className="transition hover:bg-brand-paper/50">
                  <td className="py-4 pr-4 font-semibold text-brand-ink">
                    <div className="flex items-center gap-2">
                      <FileText className="h-4 w-4 text-brand-muted" />
                      <span>{report.title}</span>
                    </div>
                  </td>
                  <td className="py-4 pr-4 font-brand-mono text-xs text-brand-muted">
                    {report.date}
                  </td>
                  <td className="py-4 pr-4 text-sm text-brand-ink">
                    {report.orderedBy}
                  </td>
                  <td className="py-4 pr-4">
                    <span className="inline-block rounded-full bg-brand-success-tint px-2.5 py-0.5 text-xs font-semibold text-brand-success">
                      Reviewed
                    </span>
                  </td>
                  <td className="py-4 text-right">
                    <button
                      type="button"
                      onClick={() => setSelectedReport(report)}
                      className="font-semibold text-brand-primary-dark hover:underline cursor-pointer text-xs"
                    >
                      View Report
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Lab Report Modal */}
      {selectedReport && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-brand-ink/40 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-2xl border border-brand-line bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-brand-line pb-3">
              <div>
                <h3 className="text-base font-bold text-brand-ink">{selectedReport.title}</h3>
                <p className="text-xs text-brand-muted">Date: {selectedReport.date} · {selectedReport.category}</p>
              </div>
              <span
                className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                  selectedReport.status === "NEW"
                    ? "bg-brand-amber-tint text-brand-amber"
                    : "bg-brand-success-tint text-brand-success"
                }`}
              >
                {selectedReport.status === "NEW" ? "New Result" : "Reviewed by Physician"}
              </span>
            </div>

            <div className="my-5 rounded-xl bg-brand-paper p-4 text-xs font-mono space-y-2 border border-brand-line">
              <div className="flex justify-between border-b border-brand-line pb-1 font-bold text-brand-ink">
                <span>PARAMETER</span>
                <span>RESULT</span>
                <span>REFERENCE</span>
              </div>
              <div className="flex justify-between text-brand-ink">
                <span>Hemoglobin</span>
                <span className="font-bold">14.2 g/dL</span>
                <span className="text-brand-muted">13.5 - 17.5</span>
              </div>
              <div className="flex justify-between text-brand-ink">
                <span>Platelets</span>
                <span className="font-bold">245,000 /µL</span>
                <span className="text-brand-muted">150,000 - 450,000</span>
              </div>
              <div className="flex justify-between text-brand-ink">
                <span>WBC Count</span>
                <span className="font-bold">6,800 /µL</span>
                <span className="text-brand-muted">4,500 - 11,000</span>
              </div>
            </div>

            <div className="flex justify-between items-center pt-2">
              <span className="text-xs text-brand-muted">Physician: {selectedReport.orderedBy}</span>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => alert(`Downloading PDF for ${selectedReport.title}...`)}
                  className="flex items-center gap-1.5 rounded-lg border border-brand-primary px-3 py-1.5 text-xs font-semibold text-brand-primary-dark hover:bg-brand-primary-tint cursor-pointer"
                >
                  <Download className="h-3.5 w-3.5" />
                  <span>Download PDF</span>
                </button>
                <button
                  type="button"
                  onClick={() => setSelectedReport(null)}
                  className="rounded-lg bg-brand-ink px-4 py-1.5 text-xs font-semibold text-white hover:bg-slate-800 cursor-pointer"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
