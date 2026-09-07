import { api } from "./api";
import type { ApiResponse } from "../types/api";
import type { AuthUser } from "../types/auth";

// Uses the shared `api` instance (withCredentials: true) so the refresh token cookie
// is sent automatically — the request body is empty because the backend reads the
// token from the cookie. Safe from recursion: the response interceptor in api.ts
// excludes /auth/* endpoints from retry, so a 401 here is never retried.
async function performRefresh(): Promise<AuthUser | null> {
  try {
    const response = await api.post<ApiResponse<AuthUser>>("/auth/refresh");
    return response.data.data;
  } catch {
    // The refresh token is expired, revoked, or already rotated away — the session
    // is genuinely over, so let subscribers redirect to the login page.
    return null;
  }
}

let inFlight: Promise<AuthUser | null> | null = null;

/**
 * Exchanges the refresh-token cookie for a new access/refresh token pair, returning
 * null when the session cannot be renewed. Concurrent callers share one request so
 * a burst of parallel 401s cannot rotate the refresh token more than once.
 */
export function refreshSession(): Promise<AuthUser | null> {
  inFlight ??= performRefresh().finally(() => {
    inFlight = null;
  });
  return inFlight;
}
