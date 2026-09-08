import { useState } from "react";
import { Bell, Check, ChevronLeft } from "lucide-react";
import { Link, useOutletContext } from "react-router-dom";

import { PATIENT_PATHS } from "../../../lib/patientRoutes";
import type { PatientPortalContext } from "../../../components/patient/PatientLayout";

export function NotificationsView() {
  const { notifications, notificationsLoading, markNotificationRead, markAllNotificationsRead } =
    useOutletContext<PatientPortalContext>();
  const [busyId, setBusyId] = useState<string | null>(null);
  const [markingAll, setMarkingAll] = useState(false);

  const unread = notifications.filter((n) => n.unread);

  const handleMarkRead = async (id: string): Promise<void> => {
    setBusyId(id);
    try {
      await markNotificationRead(id);
    } finally {
      setBusyId(null);
    }
  };

  const handleMarkAllRead = async (): Promise<void> => {
    setMarkingAll(true);
    try {
      await markAllNotificationsRead();
    } finally {
      setMarkingAll(false);
    }
  };

  return (
    <div className="space-y-5">
      <Link
        to={PATIENT_PATHS.dashboard}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-brand-primary-dark hover:underline"
      >
        <ChevronLeft className="h-4 w-4" />
        Back to Dashboard
      </Link>

      <div className="rounded-2xl border border-brand-line bg-white shadow-sm">
        <div className="border-b border-brand-line px-5 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-lg font-bold text-brand-ink">Notifications</h1>
              <p className="text-sm text-brand-muted">
                {unread.length > 0
                  ? `${unread.length} unread`
                  : "You're all caught up."}
              </p>
            </div>
            {unread.length > 0 && (
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => void handleMarkAllRead()}
                  disabled={markingAll}
                  className="inline-flex items-center gap-1 rounded-lg border border-brand-line px-2.5 py-1 text-xs font-medium text-brand-primary-dark transition hover:bg-brand-primary-tint disabled:opacity-50"
                >
                  <Check className="h-3 w-3" />
                  Mark all as read
                </button>
                <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-brand-rust px-2 text-xs font-semibold text-white">
                  {unread.length}
                </span>
              </div>
            )}
          </div>
        </div>

        <div className="divide-y divide-brand-line/60">
          {notificationsLoading ? (
            <p className="px-5 py-6 text-sm text-brand-muted">Loading notifications…</p>
          ) : notifications.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-5 py-10 text-center">
              <Bell className="h-8 w-8 text-brand-line" />
              <p className="text-sm text-brand-muted">No notifications yet.</p>
            </div>
          ) : (
            notifications.map((notification) => (
              <div
                key={notification.id}
                className="flex items-start gap-3 px-5 py-4"
              >
                <span
                  className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${
                    notification.unread
                      ? "bg-brand-rust/10 text-brand-rust"
                      : "bg-brand-primary-tint text-brand-primary-dark"
                  }`}
                >
                  <Bell className="h-3.5 w-3.5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2">
                    <span
                      className={`block text-sm ${
                        notification.unread ? "font-semibold text-brand-ink" : "text-brand-ink"
                      }`}
                    >
                      {notification.title}
                    </span>
                    {notification.unread && (
                      <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-brand-rust" aria-label="Unread" />
                    )}
                  </span>
                  <span className="block text-sm text-brand-muted">{notification.description}</span>
                </span>
                <span className="shrink-0 self-center text-xs text-brand-muted">
                  {notification.timeAgo}
                </span>
                {notification.unread && (
                  <button
                    type="button"
                    onClick={() => void handleMarkRead(notification.id)}
                    disabled={busyId === notification.id}
                    className="inline-flex items-center gap-1 rounded-lg border border-brand-line px-2.5 py-1 text-xs font-medium text-brand-primary-dark transition hover:bg-brand-primary-tint disabled:opacity-50"
                  >
                    <Check className="h-3 w-3" />
                    Read
                  </button>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
