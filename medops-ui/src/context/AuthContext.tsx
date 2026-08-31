import { createContext, useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

import { isAccessTokenExpired, parseAuthUser } from "../lib/jwt";
import {
  clearStoredTokens,
  readStoredTokens,
  subscribeToStoredTokens,
  writeStoredTokens,
} from "../lib/tokenStorage";
import {
  login as loginRequest,
  logout as logoutRequest,
  registerDoctor as registerDoctorRequest,
  registerPatient as registerPatientRequest,
} from "../services/authService";
import { refreshSession } from "../services/sessionRefresh";
import type { AuthTokens, AuthUser, RegisterDoctorRequest, RegisterPatientRequest } from "../types/auth";

export interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  registerPatient: (request: RegisterPatientRequest) => Promise<AuthUser>;
  registerDoctor: (request: RegisterDoctorRequest) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null); // oxlint-disable-line react/only-export-components

export function AuthProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [tokens, setTokens] = useState<AuthTokens | null>(readStoredTokens);
  const user = useMemo(() => (tokens ? parseAuthUser(tokens.accessToken) : null), [tokens]);

  // Token storage is also written by the axios refresh interceptor, so mirror it
  // here instead of assuming this provider is the only writer.
  useEffect(() => subscribeToStoredTokens(setTokens), []);

  // On load an access token may already have lapsed (e.g. a tab left open). Renew it
  // up front so a stale session is resolved even on a view that issues no requests.
  useEffect(() => {
    const stored = readStoredTokens();
    if (stored && isAccessTokenExpired(stored.accessToken)) {
      void refreshSession();
    }
  }, []);

  const applyTokens = useCallback((nextTokens: AuthTokens): AuthUser => {
    const nextUser = parseAuthUser(nextTokens.accessToken);
    if (!nextUser) {
      throw new Error("Signed in successfully but the session token could not be read.");
    }

    writeStoredTokens(nextTokens);
    return nextUser;
  }, []);

  const login = useCallback(
    async (email: string, password: string) => applyTokens(await loginRequest({ email, password })),
    [applyTokens],
  );

  const registerPatient = useCallback(
    async (request: RegisterPatientRequest) => applyTokens(await registerPatientRequest(request)),
    [applyTokens],
  );

  const registerDoctor = useCallback(
    async (request: RegisterDoctorRequest) => applyTokens(await registerDoctorRequest(request)),
    [applyTokens],
  );

  const logout = useCallback(async () => {
    if (tokens) {
      // Best-effort: revoke the refresh token server-side, but log out locally
      // either way so a network failure never traps the user in a signed-in UI.
      try {
        await logoutRequest(tokens.refreshToken);
      } catch {
        // ignored — local session is cleared below regardless
      }
    }
    clearStoredTokens();
  }, [tokens]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: user !== null, login, registerPatient, registerDoctor, logout }),
    [user, login, registerPatient, registerDoctor, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
