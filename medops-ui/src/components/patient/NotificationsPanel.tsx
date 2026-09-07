import { Bell } from "lucide-react";

import type { NotificationItem } from "../../types/patient";

interface NotificationsPanelProps {
  notifications: NotificationItem[];
  loading?: boolean;
  onMarkRead?: (notificationId: string) => void;
}

// Icon chips alternate teal/amber by position, matching the stat cards above.
const CHIP_STYLES = ["bg-brand-primary-tint text-brand-primary-dark", "bg-brand-amber-tint text-brand-amber"];

export function NotificationsPanel({
  notifications,
  loading = false,
  onMarkRead,
}: Readonly<NotificationsPanelProps>) {
  return (
    <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <h2 className="font-bold text-brand-ink">Reminders</h2>
        <button type="button" className="text-xs font-semibold text-brand-primary-dark hover:underline">
          View All
        </button>
      </div>

      <div className="mt-3 divide-y divide-brand-line/60">
        {loading ? (
          <p className="py-4 text-sm text-brand-muted">Loading notifications…</p>
        ) : notifications.length === 0 ? (
          <p className="py-4 text-sm text-brand-muted">No recent activity yet.</p>
        ) : (
          notifications.map((notification, index) => (
            <button
              key={notification.id}
              type="button"
              onClick={notification.unread && onMarkRead ? () => onMarkRead(notification.id) : undefined}
              disabled={!notification.unread || !onMarkRead}
              className="flex w-full cursor-pointer items-start gap-3 py-2.5 text-left first:pt-0 last:pb-0 disabled:cursor-default"
            >
              <span
                className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${CHIP_STYLES[index % CHIP_STYLES.length]}`}
              >
                <Bell className="h-3.5 w-3.5" />
              </span>
              <span className="min-w-0 flex-1">
                <span
                  className={`block truncate text-sm ${notification.unread ? "font-semibold text-brand-ink" : "text-brand-ink"}`}
                >
                  {notification.title}
                </span>
                <span className="block truncate text-xs text-brand-muted">{notification.description}</span>
              </span>
              {notification.unread && (
                <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand-rust" aria-hidden="true" />
              )}
              <span className="text-xs whitespace-nowrap text-brand-muted">{notification.timeAgo}</span>
            </button>
          ))
        )}
      </div>
    </div>
  );
}
