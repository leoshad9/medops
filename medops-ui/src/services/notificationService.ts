import type { ApiResponse } from "../types/api";
import { messageFromApiError } from "../lib/apiError";
import { api } from "./api";

export type ApiNotificationType = "APPOINTMENT_BOOKED" | "REPORT_UPLOADED";

/** Mirrors the backend {@code NotificationResponse} payload. */
export interface NotificationDto {
  id: string;
  type: ApiNotificationType;
  title: string;
  message: string;
  referenceType: string | null;
  referenceId: string | null;
  read: boolean;
  createdAt: string;
}

interface NotificationPageDto {
  items: NotificationDto[];
  page: number;
  size: number;
  total: number;
}

export async function listNotifications(page = 0, size = 20): Promise<NotificationPageDto> {
  try {
    const response = await api.get<ApiResponse<NotificationPageDto>>("/v1/notifications", {
      params: { page, size },
    });
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load notifications. Please try again."));
  }
}

export async function getUnreadNotificationCount(): Promise<number> {
  try {
    const response = await api.get<ApiResponse<{ unread: number }>>("/v1/notifications/unread-count");
    return response.data.data.unread;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to load notifications. Please try again."));
  }
}

export async function markNotificationRead(notificationId: string): Promise<NotificationDto> {
  try {
    const response = await api.patch<ApiResponse<NotificationDto>>(
      `/v1/notifications/${notificationId}/read`,
    );
    return response.data.data;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to mark that notification as read. Please try again."));
  }
}

/** Marks every unread notification as read; returns the number that were updated. */
export async function markAllNotificationsRead(): Promise<number> {
  try {
    const response = await api.post<ApiResponse<{ marked: number }>>("/v1/notifications/read-all");
    return response.data.data.marked;
  } catch (error) {
    throw new Error(messageFromApiError(error, "Unable to mark notifications as read. Please try again."));
  }
}

// --- Realtime stream (server-sent events over fetch) ---

export interface NotificationStreamPayload {
  id: string;
  type: ApiNotificationType;
  title: string;
  message: string;
  referenceType: string | null;
  referenceId: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationStreamHandlers {
  onNotification: (notification: NotificationStreamPayload) => void;
  /** Called after each successful connection, including reconnects. */
  onOpen?: () => void;
  onError?: (message: string) => void;
}

const STREAM_PATH = "/api/v1/notifications/stream";
const RECONNECT_BASE_DELAY_MS = 1000;
const RECONNECT_MAX_DELAY_MS = 30000;

/**
 * Subscribes to the realtime notification stream over fetch (the access token is
 * an HttpOnly cookie, so EventSource cannot send it). Reconnects with capped
 * exponential backoff until unsubscribed; an auth rejection ends the stream
 * for good. Returns an unsubscribe function.
 */
export function subscribeToNotificationStream(handlers: NotificationStreamHandlers): () => void {
  const controller = new AbortController();
  let stopped = false;
  let attempt = 0;

  void connect();

  return () => {
    stopped = true;
    controller.abort();
  };

  async function connect(): Promise<void> {
    while (!stopped && !controller.signal.aborted) {
      try {
        const response = await fetch(STREAM_PATH, {
          method: "GET",
          credentials: "include",
          signal: controller.signal,
        });
        if (stopped || controller.signal.aborted) {
          return;
        }
        if (!response.ok || !response.body) {
          if (response.status === 401 || response.status === 403) {
            handlers.onError?.("Realtime notifications unavailable. Please sign in again.");
            return;
          }
          throw new Error(`Notification stream responded with status ${response.status}`);
        }
        attempt = 0;
        handlers.onOpen?.();
        await readEventStream(response.body, handlers);
      } catch (error) {
        if (stopped || controller.signal.aborted) {
          return;
        }
        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }
        handlers.onError?.(
          error instanceof Error ? error.message : "Realtime notifications disconnected.",
        );
      }
      if (stopped || controller.signal.aborted) {
        return;
      }
      const delay = Math.min(RECONNECT_BASE_DELAY_MS * 2 ** attempt, RECONNECT_MAX_DELAY_MS);
      attempt += 1;
      await sleep(delay, controller.signal);
    }
  }
}

async function readEventStream(
  body: ReadableStream<Uint8Array>,
  handlers: NotificationStreamHandlers,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        return;
      }
      buffer += decoder.decode(value, { stream: true });
      buffer = dispatchCompleteEvents(buffer, handlers);
    }
  } finally {
    reader.releaseLock();
  }
}

function dispatchCompleteEvents(buffer: string, handlers: NotificationStreamHandlers): string {
  let event = "";
  let data = "";
  let newlineIndex = buffer.indexOf("\n");
  while (newlineIndex !== -1) {
    const line = buffer.slice(0, newlineIndex).replace(/\r$/, "");
    buffer = buffer.slice(newlineIndex + 1);
    newlineIndex = buffer.indexOf("\n");
    if (line === "") {
      // Blank line closes the event frame.
      if (data) {
        dispatchEvent(event, data, handlers);
      }
      event = "";
      data = "";
    } else if (line.startsWith(":")) {
      // Keepalive comment heartbeat — ignore.
    } else if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      data += (data ? "\n" : "") + line.slice(5).trimStart();
    }
  }
  return buffer;
}

function dispatchEvent(event: string, data: string, handlers: NotificationStreamHandlers): void {
  if (event !== "notification") {
    return;
  }
  try {
    handlers.onNotification(JSON.parse(data) as NotificationStreamPayload);
  } catch {
    // Ignore malformed payloads; the feed recovers on the next page load.
  }
}

function sleep(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener(
      "abort",
      () => {
        clearTimeout(timer);
        resolve();
      },
      { once: true },
    );
  });
}

