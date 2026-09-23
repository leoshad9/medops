import { AlertTriangle, ArrowUpRight, Bell, CheckCircle, FlaskConical, Pill, UserRound } from "lucide-react";
import { Link } from "react-router-dom";

import type { ClinicalAlert } from "../../types/doctor";

const SEVERITY_STYLES: Record<ClinicalAlert["severity"], { badge: string; icon: typeof AlertTriangle }> = {
  CRITICAL: { badge: "bg-brand-rust text-white", icon: AlertTriangle },
  ABNORMAL: { badge: "bg-amber-100 text-amber-800", icon: AlertTriangle },
  WARNING: { badge: "bg-brand-primary-tint text-brand-primary-dark", icon: Bell },
};

const CATEGORY_ICON: Record<ClinicalAlert["category"], typeof FlaskConical> = {
  LAB: FlaskConical,
  PRESCRIPTION: Pill,
  NOTIFICATION: Bell,
  FOLLOWUP: UserRound,
  ALLERGY: AlertTriangle,
};

interface UrgentAlertsPanelProps {
  alerts: ClinicalAlert[];
  /** Acknowledge a lab result or notification alert. Audit logging is recorded
   * by the caller so the action is attributable in the dashboard audit trail. */
  onAcknowledge?: (alert: ClinicalAlert) => void;
}

export function UrgentAlertsPanel({ alerts, onAcknowledge }: Readonly<UrgentAlertsPanelProps>) {
  const criticalCount = alerts.filter((a) => a.severity === "CRITICAL").length;
  const criticalAnnouncement =
    criticalCount > 0
      ? `${criticalCount} critical clinical alert${criticalCount === 1 ? "" : "s"} require your attention`
      : null;

  return (
    <section aria-label="Urgent clinical alerts">
      {/* Screen-reader announcement for critical alerts (assertive so they
          interrupt the current announcement queue when they arrive). */}
      {criticalAnnouncement && (
        <div
          aria-live="assertive"
          aria-atomic="true"
          className="sr-only"
          data-testid="critical-alert-announcement"
        >
          {criticalAnnouncement}
        </div>
      )}

      {alerts.length === 0 ? (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-6">
          <div className="flex items-center gap-2">
            <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
              <AlertTriangle className="h-4 w-4" />
            </span>
            <h2 className="text-base font-bold text-emerald-800">No urgent clinical alerts</h2>
          </div>
          <p className="mt-1 text-sm text-emerald-700/80">
            All diagnostic reports have been reviewed and no notifications require acknowledgement.
          </p>
        </div>
      ) : (
        <div className="rounded-xl border border-brand-line bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between border-b border-brand-line pb-3">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 text-brand-rust" />
              <h2 className="text-sm font-bold text-brand-ink">Urgent Clinical Alerts</h2>
            </div>
            <span className="rounded-full bg-brand-rust-tint px-2 py-0.5 text-xs font-semibold text-brand-rust">
              {alerts.length} action needed
            </span>
          </div>

          <ul className="mt-4 space-y-3" data-testid="urgent-alert-list">
            {alerts.map((alert) => {
              const severity = SEVERITY_STYLES[alert.severity];
              const SeverityIcon = severity.icon;
              const CategoryIcon = CATEGORY_ICON[alert.category];
              const canAcknowledge = Boolean(onAcknowledge) && (alert.category === "LAB" || alert.category === "NOTIFICATION");

              return (
                <li
                  key={alert.id}
                  className="flex items-start justify-between gap-3 rounded-lg border border-brand-line/70 bg-slate-50/40 p-3"
                  data-testid="urgent-alert"
                >
                  <div className="flex min-w-0 items-start gap-3">
                    <span
                      className={`inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${severity.badge}`}
                      aria-label={alert.severity}
                    >
                      <SeverityIcon className="h-4 w-4" />
                    </span>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-xs font-bold uppercase tracking-wider text-brand-muted">
                          {alert.severity}
                        </span>
                        <span className="inline-flex items-center gap-1 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold text-slate-700">
                          <CategoryIcon className="h-3 w-3" aria-hidden="true" />
                          {alert.category}
                        </span>
                      </div>
                      <p className="mt-0.5 text-sm font-semibold text-brand-ink">{alert.title}</p>
                      <p className="text-xs text-brand-muted">{alert.detail}</p>
                      {alert.patientName && (
                        <p className="mt-1 font-mono text-xs text-brand-muted">
                          {alert.patientName} · {alert.patientMrn}
                        </p>
                      )}
                    </div>
                  </div>

                  <div className="flex shrink-0 items-center gap-2">
                    {canAcknowledge && (
                      <button
                        type="button"
                        onClick={() => onAcknowledge?.(alert)}
                        aria-label={`Acknowledge ${alert.title}`}
                        className="inline-flex items-center gap-1 rounded-md border border-brand-line bg-white px-2.5 py-1 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary hover:text-white"
                      >
                        <CheckCircle className="h-3 w-3" />
                        Acknowledge
                      </button>
                    )}
                    <Link
                      to={alert.actionPath}
                      title={alert.actionLabel}
                      className="inline-flex shrink-0 items-center gap-1 rounded-md border border-brand-line bg-white px-2.5 py-1 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary hover:text-white"
                    >
                      {alert.actionLabel}
                      <ArrowUpRight className="h-3 w-3" />
                    </Link>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </section>
  );
}
