import { useCallback, useEffect, useRef, useState } from "react";

import { relativeTimeAgo } from "../lib/clinicTime";
import {
  getUnreadNotificationCount,
  listNotifications,
  markAllNotificationsRead,
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
  markAllRead: () => Promise<void>;
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
 * SSE stream. One instance per mounted layout (patient or doctor) - the stream
 * connection lives as long as the component that calls this hook.
 */
export function useNotifications(): UseNotificationsResult {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const cancelledRef = useRef(false);
  // Ids for which the user has optimistically flipped the row to read. A page
  // snapshot built BEFORE the PATCH committed may still report these as unread;
  // merging such a page back would make the row "go away and then come back". We
  // therefore never downgrade a pending id until a server page confirms read=true.
  const readPendingRef = useRef(new Set<string>());

  const releaseConfirmed = (items: NotificationDto[]): void => {
    const pending = readPendingRef.current;
    for (const item of items) {
      if (item.read) {
        pending.delete(item.id);
      }
    }
  };

  const applyPage = (items: NotificationDto[]): void => {
    releaseConfirmed(items);
    const pending = readPendingRef.current;
    const incoming = items.map((dto) => {
      const item = toItemFromDto(dto);
      return pending.has(item.id) ? { ...item, unread: false } : item;
    });
    setNotifications((current) => mergeById(current, incoming));
  };

  /**
   * Refetches the page and unread badge from the server and applies the result
   * through the pending guard. Returns whether the refresh succeeded so callers
   * can decide whether to surface an error. Never clears error state itself.
   */
  const refreshState = async (): Promise<boolean> => {
    try {
      const [page, unread] = await Promise.all([
        listNotifications(0, PAGE_SIZE),
        getUnreadNotificationCount(),
      ]);
      if (cancelledRef.current) {
        return false;
      }
      applyPage(page.items);
      setUnreadCount(unread);
      return true;
    } catch {
      return false;
    }
  };

  useEffect(() => {
    // One shared loader for mount and stream reconnects; merging (not replacing)
    // keeps items that arrived through the stream while the request was running.
    const fetchAll = async (): Promise<void> => {
      const ok = await refreshState();
      if (!cancelledRef.current) {
        setError(ok ? null : "Unable to load notifications.");
      }
    };

    void fetchAll().finally(() => {
      if (!cancelledRef.current) {
        setLoading(false);
      }
    });

    const unsubscribe = subscribeToNotificationStream({
      onNotification: (payload) => {
        if (cancelledRef.current) {
          return;
        }
        const incoming = toItemFromPayload(payload);
        setNotifications((current) => mergeById(current, [incoming]));
        if (incoming.unread) {
          setUnreadCount((current) => current + 1);
        }
      },
      onOpen: () => {
        if (cancelledRef.current) {
          return;
        }
        // Clear stale stream errors on a successful (re)connect, then recover
        // anything published while the connection was down (the backend replays
        // from PostgreSQL). The duplicate initial request is intentional: the
        // mount fetch above races the first open.
        setError(null);
        void refreshState().then(ok => {
          if (!ok && !cancelledRef.current) {
            setError("Unable to load notifications.");
          }
        });
      },
      onError: (message) => {
        if (!cancelledRef.current) {
          setError(message);
        }
      },
    });

    return () => {
      cancelledRef.current = true;
      unsubscribe();
    };
  }, []);

  const markRead = useCallback(async (notificationId: string): Promise<void> => {
    // Optimistic: the row and badge update immediately, then re-sync with the
    // server. The pending guard keeps the flip visible even if a stale page
    // snapshot (raced against the PATCH) lands after the optimistic update.
    readPendingRef.current.add(notificationId);
    setNotifications((current) =>
      current.map((item) => (item.id === notificationId ? { ...item, unread: false } : item)),
    );
    try {
      await markNotificationRead(notificationId);
      setNotifications((current) =>
        current.map((item) => (item.id === notificationId ? { ...item, unread: false } : item)),
      );
      void refreshState();
    } catch (err: unknown) {
      readPendingRef.current.delete(notificationId);
      setError(err instanceof Error ? err.message : "Unable to mark that notification as read.");
      void refreshState();
    }
  }, []);

  const markAllRead = useCallback(async (): Promise<void> => {
    const ids = notifications.filter((item) => item.unread).map((item) => item.id);
    ids.forEach((id) => readPendingRef.current.add(id));
    setNotifications((current) => current.map((item) => ({ ...item, unread: false })));
    setUnreadCount(0);
    try {
      await markAllNotificationsRead();
      void refreshState();
    } catch (err: unknown) {
      ids.forEach((id) => readPendingRef.current.delete(id));
      setError(err instanceof Error ? err.message : "Unable to mark notifications as read.");
      void refreshState();
    }
  }, [notifications]);

  return { notifications, unreadCount, loading, error, markRead, markAllRead };
}
