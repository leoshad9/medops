import { useEffect, useRef } from "react";
import { Bell, Check, ChevronLeft, X } from "lucide-react";
import { useNavigate } from "react-router-dom";

import type { NotificationItem } from "../../types/patient";

const CHIP_TEAL = "bg-brand-primary-tint text-brand-primary-dark";

export interface NotificationDropdownProps {
  notifications: NotificationItem[];
  unreadCount: number;
  loading: boolean;
  viewAllPath: string;
  markAllIconSize?: string;
  onMarkRead: (notificationId: string) => Promise<void>;
  onMarkAllRead: () => Promise<void>;
  onClose: () => void;
}

export function NotificationDropdown({
  notifications,
  unreadCount,
  loading,
  viewAllPath,
  markAllIconSize = "h-3.5",
  onMarkRead,
  onMarkAllRead,
  onClose,
}: Readonly<NotificationDropdownProps>) {
  const dropdownRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        onClose();
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose]);

  const unread = notifications.filter((n) => n.unread);

  const handleMarkRead = async (id: string, event: React.MouseEvent): Promise<void> => {
    event.stopPropagation();
    await onMarkRead(id);
  };

  const handleMarkAllRead = async (event: React.MouseEvent): Promise<void> => {
    event.stopPropagation();
    await onMarkAllRead();
  };

  const handleViewAll = (event: React.MouseEvent): void => {
    event.stopPropagation();
    navigate(viewAllPath);
    onClose();
  };

  return (
    <div
      ref={dropdownRef}
      className="fixed top-16 right-4 z-50 w-96 max-w-[calc(100vw-1rem)] rounded-2xl border border-brand-line bg-white shadow-lg overflow-hidden animate-in fade-in-0 zoom-in-95 duration-150"
      role="menu"
      aria-label="Notifications"
    >
      <div className="flex items-center justify-between border-b border-brand-line px-4 py-3">
        <div className="flex items-center gap-2">
          <h2 className="font-bold text-brand-ink">Notifications</h2>
          {unreadCount > 0 && (
            <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-rust px-1.5 text-xs font-semibold text-white">
              {unreadCount}
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={onClose}
          className="grid h-8 w-8 place-items-center rounded-lg text-brand-muted transition hover:text-brand-ink hover:bg-slate-100"
          aria-label="Close notifications"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="max-h-[50vh] overflow-y-auto divide-y divide-brand-line/60">
        {loading ? (
          <p className="px-4 py-4 text-sm text-brand-muted text-center">Loading notifications…</p>
        ) : notifications.length === 0 ? (
          <div className="flex flex-col items-center gap-2 px-4 py-8 text-center">
            <Bell className="h-8 w-8 text-brand-line" />
            <p className="text-sm text-brand-muted">No notifications yet.</p>
          </div>
        ) : (
          notifications.map((notification) => (
            <button
              key={notification.id}
              type="button"
              onClick={notification.unread ? (e) => handleMarkRead(notification.id, e) : undefined}
              disabled={!notification.unread}
              className="flex w-full cursor-pointer items-start gap-3 p-4 text-left first:pt-4 last:pb-4 disabled:cursor-default hover:bg-slate-50 transition-colors"
              role="menuitem"
            >
              <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${CHIP_TEAL}`}>
                <Bell className="h-4 w-4" />
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

      {notifications.length > 0 && (
        <div className="border-t border-brand-line p-3">
          <div className="flex items-center justify-between gap-2">
            {unread.length > 0 && (
              <button
                type="button"
                onClick={handleMarkAllRead}
                className="inline-flex items-center gap-1.5 rounded-lg border border-brand-line px-3 py-1.5 text-xs font-medium text-brand-primary-dark transition hover:bg-brand-primary-tint"
              >
                <Check className={`${markAllIconSize} w-3.5`} />
                Mark all as read
              </button>
            )}
            <button
              type="button"
              onClick={handleViewAll}
              className="inline-flex items-center gap-1.5 rounded-lg bg-brand-primary px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-brand-primary-dark"
            >
              View All
              <ChevronLeft className="h-3.5 w-3.5 rotate-180" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
