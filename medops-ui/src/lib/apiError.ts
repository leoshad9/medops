import axios from "axios";

import type { ErrorResponse } from "../types/api";

/**
 * Maps API / network failures to a short message safe to show in the UI.
 * Never surfaces raw JS exceptions like "Cannot read properties of undefined".
 */
export function messageFromApiError(error: unknown, fallback: string): string {
  if (axios.isAxiosError<ErrorResponse>(error)) {
    const apiMessage = error.response?.data?.error?.message;
    if (typeof apiMessage === "string" && apiMessage.trim()) {
      return apiMessage.trim();
    }

    if (!error.response) {
      return "Unable to reach the server. Please try again.";
    }

    switch (error.response.status) {
      case 401:
        return "Invalid email or password";
      case 403:
        return "You do not have permission to perform this action";
      case 429:
        return "Too many attempts. Please try again later.";
      case 503:
        return "Service temporarily unavailable. Please try again later.";
      default:
        break;
    }
  }

  if (error instanceof Error) {
    const text = error.message.trim();
    // Ignore accidental TypeErrors / internal crashes leaked into Error.message
    if (text && !text.startsWith("Cannot read properties of")) {
      return text;
    }
  }

  return fallback;
}
