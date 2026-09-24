import axios from "axios";

import { getCookie } from "../lib/csrf";
import { refreshSession } from "./sessionRefresh";

export const api = axios.create({
  baseURL: "/api",
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

// The auth endpoints either need no token or manage tokens themselves, so they must
// never be intercepted for refresh.
function isAuthEndpoint(url: string | undefined): boolean {
  return url?.startsWith("/auth/") ?? false;
}

// Shared promise to avoid multiple concurrent CSRF fetches when several
// state-changing requests fire before the XSRF-TOKEN cookie is established.
let csrfInitPromise: Promise<void> | null = null;

async function fetchCsrfToken(): Promise<void> {
  try {
    await api.get("/auth/csrf");
  } catch {
    // CSRF bootstrap is best-effort: state-changing requests still go out and
    // the server rejects only what is actually unprotected. Swallowing keeps a
    // failed prefetch from blocking app boot.
  }
}

/**
 * Warms the XSRF-TOKEN cookie outside the critical path. Call once at app boot
 * (e.g. alongside the session bootstrap) so the first state-changing request
 * finds the cookie and skips the serialized GET /auth/csrf round trip that
 * otherwise blocks it inside the request interceptor.
 */
export function prefetchCsrfToken(): Promise<void> {
  if (getCookie("XSRF-TOKEN")) {
    return Promise.resolve();
  }
  if (!csrfInitPromise) {
    csrfInitPromise = fetchCsrfToken().finally(() => {
      csrfInitPromise = null;
    });
  }
  return csrfInitPromise;
}

async function ensureCsrfToken(): Promise<void> {
  if (getCookie("XSRF-TOKEN")) {
    return;
  }
  await prefetchCsrfToken();
}

function isStateChanging(method: string | undefined): boolean {
  return ["post", "put", "delete", "patch"].includes((method ?? "").toLowerCase());
}

api.interceptors.request.use(async (config) => {
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    config.headers.delete("Content-Type");
  }

  // /auth/** is CSRF-exempt server-side (SecurityConfig ignores CSRF there), so
  // fetching a token before login/refresh/logout would only add a serialized round
  // trip to a request that does not need it. Registration (POST /v1/patients and
  // /v1/doctors) is not exempt and still gets the lazy token below.
  if (isStateChanging(config.method) && !isAuthEndpoint(config.url)) {
    await ensureCsrfToken();
  }

  const csrfToken = getCookie("XSRF-TOKEN");
  if (csrfToken) {
    config.headers.set("X-XSRF-TOKEN", csrfToken);
  }

  return config;
});

// A 401 means the access token was rejected server-side (expired, or revoked between
// requests). The refresh token is an HttpOnly cookie, so retry the request — if the
// refresh-token cookie is still valid, the interceptor in sessionRefresh.ts will have
// rotated both tokens before this retry fires. Auth endpoints are excluded so a 401
// on login/refresh/logout itself is surfaced to the caller, not retried.
api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) {
      throw error;
    }

    const config = error.config;
    if (!config || isAuthEndpoint(config.url)) {
      throw error;
    }

    const refreshed = await refreshSession();
    if (!refreshed) {
      throw error;
    }

    return api.request(config);
  },
);
