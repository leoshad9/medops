import { Bell } from "lucide-react";

import type { NotificationItem } from "../../types/patient";

interface NotificationsPanelProps {
  notifications: NotificationItem[];
}

// Icon chips alternate teal/amber by position, matching the stat cards above.
const CHIP_STYLES = ["bg-brand-primary-tint text-brand-primary-dark", "bg-brand-amber-tint text-brand-amber"];

export function NotificationsPanel({ notifications }: Readonly<NotificationsPanelProps>) {
  return (
    <div className="rounded-2xl border border-brand-line bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <h2 className="font-bold text-brand-ink">Reminders</h2>
        <button type="button" className="text-xs font-semibold text-brand-primary-dark hover:underline">
          View All
        </button>
      </div>

      <div className="mt-3 divide-y divide-brand-line/60">
        {notifications.length === 0 ? (
          <p className="py-4 text-sm text-brand-muted">No recent activity yet.</p>
        ) : (
          notifications.map((notification, index) => (
          <div key={notification.id} className="flex items-start gap-3 py-2.5 first:pt-0 last:pb-0">
            <span
              className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${CHIP_STYLES[index % CHIP_STYLES.length]}`}
            >
              <Bell className="h-3.5 w-3.5" />
            </span>
            <div className="flex-1">
              <p className="text-sm font-semibold text-brand-ink">{notification.title}</p>
              <p className="text-xs text-brand-muted">{notification.description}</p>
            </div>
            <span className="text-xs whitespace-nowrap text-brand-muted">{notification.timeAgo}</span>
          </div>
          ))
        )}
      </div>
    </div>
  );
}
