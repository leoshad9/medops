import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";

import { DOCTOR_PATHS } from "../../lib/doctorRoutes";
import { formatClinicDateTime } from "../../lib/clinicTime";
import {
  createPrescription,
  downloadClinicalFile,
  listMyPatients,
  listPrescriptions,
  listReports,
  uploadReport,
  type ClinicalReportDto,
  type DoctorPatientSummary,
  type PrescriptionDto,
} from "../../services/clinicalService";

export function DoctorPatientChartView() {
  const { patientId = "" } = useParams();
  const [patient, setPatient] = useState<DoctorPatientSummary | null>(null);
  const [reports, setReports] = useState<ClinicalReportDto[]>([]);
  const [prescriptions, setPrescriptions] = useState<PrescriptionDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [reportTitle, setReportTitle] = useState("");
  const [reportNotes, setReportNotes] = useState("");
  const [reportFile, setReportFile] = useState<File | null>(null);
  const [rxName, setRxName] = useState("");
  const [rxDosage, setRxDosage] = useState("");
  const [rxInstructions, setRxInstructions] = useState("");
  const [rxRefills, setRxRefills] = useState(0);
  const [busy, setBusy] = useState(false);

  const reload = async () => {
    const [roster, reportItems, rxItems] = await Promise.all([
      listMyPatients(),
      listReports(patientId),
      listPrescriptions(patientId),
    ]);
    setPatient(roster.find((item) => item.id === patientId) ?? null);
    setReports(reportItems);
    setPrescriptions(rxItems);
  };

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      const [roster, reportItems, rxItems] = await Promise.all([
        listMyPatients(),
        listReports(patientId),
        listPrescriptions(patientId),
      ]);
      if (cancelled) {
        return;
      }
      setPatient(roster.find((item) => item.id === patientId) ?? null);
      setReports(reportItems);
      setPrescriptions(rxItems);
      setError(null);
    };
    load().catch((err: unknown) => {
      if (!cancelled) {
        setError(err instanceof Error ? err.message : "Unable to load this chart.");
      }
    });
    return () => {
      cancelled = true;
    };
  }, [patientId]);

  const onUploadReport = async (event: FormEvent) => {
    event.preventDefault();
    if (!reportFile) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await uploadReport(patientId, reportTitle, reportNotes, reportFile);
      setReportTitle("");
      setReportNotes("");
      setReportFile(null);
      setNotice("Report uploaded.");
      await reload();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Unable to upload report.");
    } finally {
      setBusy(false);
    }
  };

  const onCreateRx = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createPrescription({
        patientId,
        medicationName: rxName,
        dosage: rxDosage,
        instructions: rxInstructions,
        refillsRemaining: rxRefills,
      });
      setRxName("");
      setRxDosage("");
      setRxInstructions("");
      setRxRefills(0);
      setNotice("Prescription saved.");
      await reload();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Unable to save prescription.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      <Link to={DOCTOR_PATHS.patients} className="text-xs font-semibold text-brand-primary-dark hover:underline">
        ← Back to roster
      </Link>
      <h2 className="text-lg font-bold text-brand-ink">
        {patient ? `${patient.fullName} · ${patient.mrn}` : "Patient chart"}
      </h2>
      {error && (
        <p className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
          {error}
        </p>
      )}
      {notice && (
        <p className="rounded-xl border border-brand-success/30 bg-brand-success-tint px-4 py-3 text-sm text-brand-success">
          {notice}
        </p>
      )}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <section className="rounded-xl border border-brand-line bg-white p-6 shadow-xs space-y-4">
          <h3 className="text-sm font-bold text-brand-ink">Upload report (PDF)</h3>
          <form onSubmit={onUploadReport} className="space-y-3">
            <input
              required
              value={reportTitle}
              onChange={(e) => setReportTitle(e.target.value)}
              placeholder="Title"
              className="w-full rounded-lg border border-brand-line px-3 py-2 text-sm"
            />
            <textarea
              value={reportNotes}
              onChange={(e) => setReportNotes(e.target.value)}
              placeholder="Notes (optional)"
              rows={3}
              className="w-full rounded-lg border border-brand-line px-3 py-2 text-sm"
            />
            <label className="block text-xs font-semibold text-brand-ink">
              PDF file only
              <input
                required
                type="file"
                accept="application/pdf,.pdf"
                onChange={(e) => {
                  const next = e.target.files?.[0] ?? null;
                  if (next && next.type !== "application/pdf" && !next.name.toLowerCase().endsWith(".pdf")) {
                    setError("Only PDF files can be uploaded.");
                    e.target.value = "";
                    setReportFile(null);
                    return;
                  }
                  setError(null);
                  setReportFile(next);
                }}
                className="mt-1 w-full text-xs"
              />
            </label>
            <button
              type="submit"
              disabled={busy}
              className="rounded-lg bg-brand-primary px-4 py-2 text-xs font-semibold text-white disabled:opacity-60"
            >
              Upload report
            </button>
          </form>
          <ul className="space-y-2 text-sm">
            {reports.map((report) => (
              <li key={report.id} className="flex items-center justify-between gap-2 border-t border-brand-line pt-2">
                <span>
                  {report.title} · {formatClinicDateTime(report.createdAt)} · {report.status}
                </span>
                <button
                  type="button"
                  className="text-xs font-semibold text-brand-primary-dark hover:underline"
                  onClick={() => void downloadClinicalFile(`/v1/reports/${report.id}/file`, report.originalFilename)}
                >
                  Download
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-xl border border-brand-line bg-white p-6 shadow-xs space-y-4">
          <h3 className="text-sm font-bold text-brand-ink">Write prescription</h3>
          <form onSubmit={onCreateRx} className="space-y-3">
            <input
              required
              value={rxName}
              onChange={(e) => setRxName(e.target.value)}
              placeholder="Medication"
              className="w-full rounded-lg border border-brand-line px-3 py-2 text-sm"
            />
            <input
              required
              value={rxDosage}
              onChange={(e) => setRxDosage(e.target.value)}
              placeholder="Dosage"
              className="w-full rounded-lg border border-brand-line px-3 py-2 text-sm"
            />
            <textarea
              required
              value={rxInstructions}
              onChange={(e) => setRxInstructions(e.target.value)}
              placeholder="Instructions"
              rows={3}
              className="w-full rounded-lg border border-brand-line px-3 py-2 text-sm"
            />
            <input
              type="number"
              min={0}
              max={24}
              value={rxRefills}
              onChange={(e) => setRxRefills(Number(e.target.value))}
              className="w-full rounded-lg border border-brand-line px-3 py-2 text-sm"
            />
            <button
              type="submit"
              disabled={busy}
              className="rounded-lg bg-brand-primary px-4 py-2 text-xs font-semibold text-white disabled:opacity-60"
            >
              Save prescription
            </button>
          </form>
          <ul className="space-y-2 text-sm">
            {prescriptions.map((rx) => (
              <li key={rx.id} className="border-t border-brand-line pt-2">
                {rx.medicationName} {rx.dosage} · {formatClinicDateTime(rx.createdAt)}
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
