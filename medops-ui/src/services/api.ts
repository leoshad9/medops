import axios from "axios";
import type { InternalAxiosRequestConfig } from "axios";

import { isAccessTokenExpired } from "../lib/jwt";
import { readStoredTokens } from "../lib/tokenStorage";
import { refreshSession } from "./sessionRefresh";

export const api = axios.create({
  baseURL: "/api",
  headers: {
    "Content-Type": "application/json",
  },
});

type RetriableConfig = InternalAxiosRequestConfig & { retriedAfterRefresh?: boolean };

// The auth endpoints either need no token or manage tokens themselves, so they must
// never be intercepted for refresh.
function isAuthEndpoint(url: string | undefined): boolean {
  return url?.startsWith("/auth/") ?? false;
}

api.interceptors.request.use(async (config) => {
  if (isAuthEndpoint(config.url)) {
    return config;
  }

  let tokens = readStoredTokens();
  if (tokens && isAccessTokenExpired(tokens.accessToken)) {
    tokens = await refreshSession();
  }
  if (tokens) {
    config.headers.Authorization = `Bearer ${tokens.accessToken}`;
  }
  return config;
});

// A 401 here means the access token was rejected server-side (expired, or revoked
// between requests). Try exactly one refresh-and-retry; a second failure is final.
api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) {
      throw error;
    }

    const config = error.config as RetriableConfig | undefined;
    if (!config || config.retriedAfterRefresh || isAuthEndpoint(config.url)) {
      throw error;
    }

    const tokens = await refreshSession();
    if (!tokens) {
      throw error;
    }

    config.retriedAfterRefresh = true;
    config.headers.Authorization = `Bearer ${tokens.accessToken}`;
    return api.request(config);
  },
);
