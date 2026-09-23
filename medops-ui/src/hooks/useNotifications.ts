import { useCallback, useEffect, useRef, useState } from "react";

import { formatPatientDateTimeAbsolute, relativePart } from "../lib/clinicTime";
import { PATIENT_PATHS } from "../lib/patientRoutes";
import { DOCTOR_PATHS } from "../lib/doctorRoutes";
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

// Notification work is not needed for first paint, so it is scheduled after the
// initial render instead of racing the dashboard's own requests for connections.
const IDLE_START_TIMEOUT_MS = 1000;
const FALLBACK_START_DELAY_MS = 200;

export interface UseNotificationsResult {
  notifications: NotificationItem[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
  markRead: (notificationId: string) => Promise<void>;
  markAllRead: () => Promise<void>;
}

type NotificationRole = "patient" | "doctor";

function fromType(type: NotificationDto["type"], role: NotificationRole): {
  category: NotificationItem["category"];
  actionLabel: string;
  actionPath: string;
} {
  const paths = role === "doctor" ? DOCTOR_PATHS : PATIENT_PATHS;
  switch (type) {
    case "REPORT_UPLOADED":
      return { category: "lab", actionLabel: "View Report", actionPath: paths.labs };
    case "APPOINTMENT_BOOKED":
    default:
      return { category: "appointment", actionLabel: "View Appointment", actionPath: paths.appointments };
  }
}

function toItem(
  id: string,
  title: string,
  message: string,
  createdAt: string,
  unread: boolean,
  type: NotificationDto["type"],
  role: NotificationRole,
): NotificationItem {
  const { category, actionLabel, actionPath } = fromType(type, role);
  return {
    id,
    title,
    description: message,
    timeAgo: relativePart(createdAt),
    timeAbsolute: formatPatientDateTimeAbsolute(createdAt),
    category,
    actionLabel,
    actionPath,
    unread,
  };
}

function toItemFromDto(dto: NotificationDto, role: NotificationRole): NotificationItem {
  return toItem(dto.id, dto.title, dto.message, dto.createdAt, !dto.read, dto.type, role);
}

function toItemFromPayload(payload: NotificationStreamPayload, role: NotificationRole): NotificationItem {
  return toItem(payload.id, payload.title, payload.message, payload.createdAt, !payload.read, payload.type, role);
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
 * Runs `start` once the browser is idle after the initial render, so the notification
 * fetches and the SSE connection do not compete with the dashboard's own requests for
 * connections during boot. Browsers without requestIdleCallback (Safari, jsdom) fall
 * back to a short delay. Returns a cancel function for effect cleanup.
 */
function scheduleAfterIdle(start: () => void): () => void {
  if (typeof window.requestIdleCallback === "function") {
    const handle = window.requestIdleCallback(start, { timeout: IDLE_START_TIMEOUT_MS });
    return () => window.cancelIdleCallback(handle);
  }
  const handle = window.setTimeout(start, FALLBACK_START_DELAY_MS);
  return () => window.clearTimeout(handle);
}

/**
 * Loads the notification feed and unread badge, and keeps both live through the
 * SSE stream. One instance per mounted layout (patient or doctor) - the stream
 * connection lives as long as the component that calls this hook.
 */
export function useNotifications(role: NotificationRole = "patient"): UseNotificationsResult {
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

  const releaseConfirmed = useCallback((items: NotificationDto[]): void => {
    const pending = readPendingRef.current;
    for (const item of items) {
      if (item.read) {
        pending.delete(item.id);
      }
    }
  }, []);

  const applyPage = useCallback((items: NotificationDto[]): void => {
    releaseConfirmed(items);
    const pending = readPendingRef.current;
    const incoming = items.map((dto) => {
      const item = toItemFromDto(dto, role);
      return pending.has(item.id) ? { ...item, unread: false } : item;
    });
    setNotifications((current) => mergeById(current, incoming));
  }, [role, releaseConfirmed]);

  /**
   * Refetches the page and unread badge from the server and applies the result
   * through the pending guard. Returns whether the refresh succeeded so callers
   * can decide whether to surface an error. Never clears error state itself.
   */
  const refreshState = useCallback(async (): Promise<boolean> => {
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
  }, [applyPage]);

  useEffect(() => {
    // StrictMode mounts, cleans up, and remounts effects with the same refs; without
    // this reset the cleanup's flag would suppress every update after the remount,
    // keeping the feed stuck on its loading state in development.
    cancelledRef.current = false;

    let unsubscribe: (() => void) | null = null;

    // One shared loader for the initial load and stream reconnects; merging (not
    // replacing) keeps items that arrived through the stream while the request was
    // running.
    const fetchAll = async (): Promise<void> => {
      const ok = await refreshState();
      if (!cancelledRef.current) {
        setError(ok ? null : "Unable to load notifications.");
      }
    };

    // Deferred until after the first render so the dashboard's critical requests
    // (profile, appointments, prescriptions, reports) go out first. The stream
    // replays anything published while it was not yet connected.
    const start = (): void => {
      if (cancelledRef.current) {
        return;
      }

      void fetchAll().finally(() => {
        if (!cancelledRef.current) {
          setLoading(false);
        }
      });

      unsubscribe = subscribeToNotificationStream({
        onNotification: (payload) => {
          if (cancelledRef.current) {
            return;
          }
          const incoming = toItemFromPayload(payload, role);
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
    };

    const cancelScheduledStart = scheduleAfterIdle(start);

    return () => {
      cancelledRef.current = true;
      cancelScheduledStart();
      unsubscribe?.();
    };
  }, [role, refreshState]);

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
  }, [refreshState]);

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
  }, [notifications, refreshState]);

  return { notifications, unreadCount, loading, error, markRead, markAllRead };
}
