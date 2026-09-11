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

api.interceptors.request.use((config) => {
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    config.headers.delete("Content-Type");
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
