import { useState } from "react";
import { Download, FileText, FlaskConical } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";
import type { MedicalDocumentItem } from "../../../types/patient";

export function MedicalRecordsView() {
  const { data } = usePatientPortal();
  const documents = data.medicalDocuments;
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  const handleDownload = (doc: MedicalDocumentItem) => {
    setDownloadingId(doc.id);
    setTimeout(() => {
      setDownloadingId(null);
      alert(`Downloaded document: ${doc.title} (${doc.fileSize ?? "PDF"})`);
    }, 400);
  };

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="flex items-center justify-between border-b border-brand-line pb-4 mb-4">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-brand-ink">Medical Records & Documents</h2>
              <span className="text-xs font-semibold text-brand-muted bg-brand-paper px-2.5 py-0.5 rounded-full border border-brand-line">
                {documents.length} files
              </span>
            </div>
            <p className="text-xs text-brand-muted mt-0.5">
              Access and download your complete clinical records, visit notes, and hospital discharge summaries.
            </p>
          </div>
        </div>

        <div className="divide-y divide-brand-line">
          {documents.map((doc) => {
            const isLab = doc.type === "LAB";
            const isDownloading = downloadingId === doc.id;

            return (
              <div
                key={doc.id}
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
                    {isLab ? <FlaskConical className="h-4.5 w-4.5" /> : <FileText className="h-4.5 w-4.5" />}
                  </span>
                  <div>
                    <h3 className="text-sm font-semibold text-brand-ink">{doc.title}</h3>
                    <p className="text-xs text-brand-muted mt-0.5">
                      {doc.date} · {doc.provider} {doc.fileSize && `· ${doc.fileSize}`}
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => handleDownload(doc)}
                  className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition cursor-pointer ${
                    isDownloading
                      ? "bg-brand-success-tint text-brand-success"
                      : "text-brand-primary-dark hover:bg-brand-primary-tint"
                  }`}
                >
                  <Download className="h-3.5 w-3.5" />
                  <span>{isDownloading ? "Downloading..." : "Download"}</span>
                </button>
              </div>
            );
          })}
        </div>

        <p className="mt-4 pt-3 border-t border-brand-line text-xs text-brand-muted text-center">
          Showing {documents.length} clinical records stored securely in your patient vault.
        </p>
      </section>
    </div>
  );
}
