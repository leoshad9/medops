import axios from "axios";

import { clearStoredTokens, readStoredTokens, writeStoredTokens } from "../lib/tokenStorage";
import type { ApiResponse } from "../types/api";
import type { AuthTokens } from "../types/auth";

// Deliberately a bare axios call rather than the shared `api` instance: routing the
// refresh through the same interceptors that trigger it would recurse on failure.
async function performRefresh(): Promise<AuthTokens | null> {
  const current = readStoredTokens();
  if (!current) {
    return null;
  }

  try {
    const response = await axios.post<ApiResponse<AuthTokens>>(
      "/api/auth/refresh",
      { refreshToken: current.refreshToken },
      { headers: { "Content-Type": "application/json" } },
    );
    const tokens = response.data.data;
    writeStoredTokens(tokens);
    return tokens;
  } catch {
    // The refresh token is expired, revoked, or already rotated away — the session
    // is genuinely over, so drop it and let subscribers redirect to the login page.
    clearStoredTokens();
    return null;
  }
}

let inFlight: Promise<AuthTokens | null> | null = null;

/**
 * Exchanges the stored refresh token for a new token pair, returning null when the
 * session cannot be renewed. Concurrent callers share one request so a burst of
 * parallel 401s cannot rotate the refresh token more than once.
 */
export function refreshSession(): Promise<AuthTokens | null> {
  inFlight ??= performRefresh().finally(() => {
    inFlight = null;
  });
  return inFlight;
}
