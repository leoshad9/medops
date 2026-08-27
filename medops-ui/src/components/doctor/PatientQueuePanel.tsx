import { AlertTriangle, Clock, MapPin, UserCheck } from "lucide-react";

import type { PatientQueueItem } from "../../types/doctor";

interface PatientQueuePanelProps {
  queue: PatientQueueItem[];
}

export function PatientQueuePanel({ queue }: Readonly<PatientQueuePanelProps>) {
  return (
    <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
      <div className="flex items-center justify-between border-b border-brand-line pb-3">
        <div>
          <h2 className="text-sm font-bold text-brand-ink">Live Patient Queue</h2>
          <p className="text-xs text-brand-muted">Checked-in patients waiting for consultation</p>
        </div>
        <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-700">
          {queue.length} Ready
        </span>
      </div>

      <div className="mt-4 space-y-3">
        {queue.map((item) => (
          <div
            key={item.id}
            className="flex flex-col justify-between gap-2 rounded-lg border border-brand-line/80 bg-slate-50/50 p-3.5 transition hover:border-brand-primary/40 hover:bg-white"
          >
            <div className="flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-semibold text-brand-ink">{item.patientName}</p>
                  {item.priority === "URGENT" && (
                    <span className="inline-flex items-center gap-0.5 rounded-sm bg-red-100 px-1.5 py-0.2 text-[10px] font-bold uppercase text-red-800">
                      <AlertTriangle className="h-3 w-3" /> Urgent
                    </span>
                  )}
                  {item.priority === "HIGH" && (
                    <span className="inline-flex items-center gap-0.5 rounded-sm bg-amber-100 px-1.5 py-0.2 text-[10px] font-bold uppercase text-amber-800">
                      Priority
                    </span>
                  )}
                </div>
                <div className="mt-1 flex items-center gap-3 text-xs text-brand-muted">
                  <span className="flex items-center gap-1">
                    <MapPin className="h-3 w-3" /> {item.roomNumber}
                  </span>
                  <span className="flex items-center gap-1">
                    <Clock className="h-3 w-3" /> In: {item.checkInTime}
                  </span>
                </div>
              </div>

              <button
                type="button"
                className="flex items-center gap-1 rounded-lg bg-brand-primary px-2.5 py-1.5 text-xs font-semibold text-white shadow-xs transition hover:bg-brand-primary-dark"
              >
                <UserCheck className="h-3.5 w-3.5" />
                Call In
              </button>
            </div>

            <p className="text-xs text-slate-600 bg-white border border-brand-line/50 rounded-md p-2">
              <span className="font-medium text-slate-700">Chief complaint:</span> {item.chiefComplaint}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
