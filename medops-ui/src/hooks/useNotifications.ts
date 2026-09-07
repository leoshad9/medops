import { useCallback, useEffect, useState } from "react";

import { relativeTimeAgo } from "../lib/clinicTime";
import {
  getUnreadNotificationCount,
  listNotifications,
  markNotificationRead,
  subscribeToNotificationStream,
  type NotificationDto,
  type NotificationStreamPayload,
} from "../services/notificationService";
import type { NotificationItem } from "../types/patient";

const PAGE_SIZE = 20;

export interface UseNotificationsResult {
  notifications: NotificationItem[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
  markRead: (notificationId: string) => Promise<void>;
}

function toItem(
  id: string,
  title: string,
  message: string,
  createdAt: string,
  unread: boolean,
): NotificationItem {
  return {
    id,
    title,
    description: message,
    timeAgo: relativeTimeAgo(createdAt),
    unread,
  };
}

function toItemFromDto(dto: NotificationDto): NotificationItem {
  return toItem(dto.id, dto.title, dto.message, dto.createdAt, !dto.read);
}

function toItemFromPayload(payload: NotificationStreamPayload): NotificationItem {
  return toItem(payload.id, payload.title, payload.message, payload.createdAt, !payload.read);
}

/**
 * Merges a freshly fetched page over the current list without clobbering items
 * that arrived through the stream while the request was in flight. Server
 * ordering wins for items present in the page; the list stays capped.
 */
function mergeById(current: NotificationItem[], incoming: NotificationItem[]): NotificationItem[] {
  const incomingIds = new Set(incoming.map((item) => item.id));
  return [...incoming, ...current.filter((item) => !incomingIds.has(item.id))].slice(0, PAGE_SIZE);
}

/**
 * Loads the notification feed and unread badge, and keeps both live through the
 * SSE stream. One instance per mounted layout (patient or doctor) — the stream
 * connection lives as long as the component that calls this hook.
 */
export function useNotifications(): UseNotificationsResult {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    // One shared loader for mount and stream reconnects; merging (not replacing)
    // keeps items that arrived through the stream while the request was running.
    const fetchAll = async (): Promise<void> => {
      try {
        const [page, unread] = await Promise.all([
          listNotifications(0, PAGE_SIZE),
          getUnreadNotificationCount(),
        ]);
        if (cancelled) {
          return;
        }
        setNotifications((current) => mergeById(current, page.items.map(toItemFromDto)));
        setUnreadCount(unread);
        setError(null);
      } catch (err: unknown) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load notifications.");
        }
      }
    };

    void fetchAll().finally(() => {
      if (!cancelled) {
        setLoading(false);
      }
    });

    const unsubscribe = subscribeToNotificationStream({
      onNotification: (payload) => {
        if (cancelled) {
          return;
        }
        const incoming = toItemFromPayload(payload);
        setNotifications((current) => mergeById(current, [incoming]));
        if (incoming.unread) {
          setUnreadCount((current) => current + 1);
        }
      },
      onOpen: () => {
        if (cancelled) {
          return;
        }
        // A successful (re)connect: clear stale stream errors and recover anything
        // published while the connection was down (the backend replays from
        // PostgreSQL). The duplicate request at mount is intentional and cheap.
        setError(null);
        void fetchAll();
      },
      onError: (message) => {
        if (!cancelled) {
          setError(message);
        }
      },
    });

    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  const markRead = useCallback(async (notificationId: string): Promise<void> => {
    // Optimistic: the row and badge update immediately, then re-sync with the
    // server (list is refetched only if the call fails, to avoid drift).
    setNotifications((current) =>
      current.map((item) => (item.id === notificationId ? { ...item, unread: false } : item)),
    );
    try {
      await markNotificationRead(notificationId);
      setUnreadCount(await getUnreadNotificationCount());
      setError(null);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Unable to mark that notification as read.");
      try {
        const page = await listNotifications(0, PAGE_SIZE);
        setNotifications((current) => mergeById(current, page.items.map(toItemFromDto)));
      } catch {
        // Keep the optimistic local state; the badge stays as-is.
      }
    }
  }, []);

  return { notifications, unreadCount, loading, error, markRead };
}
