import { createContext, useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

import {
  login as loginRequest,
  logout as logoutRequest,
  registerDoctor as registerDoctorRequest,
  registerPatient as registerPatientRequest,
} from "../services/authService";
import { api } from "../services/api";
import { refreshSession } from "../services/sessionRefresh";
import type { ApiResponse } from "../types/api";
import type { AuthUser, RegisterDoctorRequest, RegisterPatientRequest } from "../types/auth";

export interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isSessionDead: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  registerPatient: (request: RegisterPatientRequest) => Promise<AuthUser>;
  registerDoctor: (request: RegisterDoctorRequest) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null); // oxlint-disable-line react/only-export-components

export function AuthProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSessionDead, setIsSessionDead] = useState(false);

  // On mount, restore the session from the HttpOnly access-token cookie by asking
  // the backend who is signed in. The cookie is not readable by JavaScript, so
  // there is no local token to parse — /auth/me is the only way to know.
  useEffect(() => {
    let cancelled = false;
    void api.get<ApiResponse<AuthUser>>("/auth/me")
      .then((response) => {
        if (!cancelled) setUser(response.data.data);
      })
      .catch(() => {
        if (!cancelled) setUser(null);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string): Promise<AuthUser> => {
    const loggedInUser = await loginRequest({ email, password });
    setUser(loggedInUser);
    return loggedInUser;
  }, []);

  const registerPatient = useCallback(
    async (request: RegisterPatientRequest): Promise<AuthUser> => {
      const registeredUser = await registerPatientRequest(request);
      setUser(registeredUser);
      return registeredUser;
    },
    [],
  );

  const registerDoctor = useCallback(
    async (request: RegisterDoctorRequest): Promise<AuthUser> => {
      const registeredUser = await registerDoctorRequest(request);
      setUser(registeredUser);
      return registeredUser;
    },
    [],
  );

  const logout = useCallback(async () => {
    try {
      await logoutRequest();
    } catch {
      // ignored — local session is cleared below regardless
    }
    setUser(null);
  }, []);

  // When the axios interceptor refreshes a 401, the new access token arrives as a
  // cookie, but the user identity may have changed (e.g. role update). Re-fetch
  // /auth/me so the UI reflects the current session. If refresh fails, the session
  // is genuinely over — flag it so the router can redirect to login.
  useEffect(() => {
    const handler = () => {
      void refreshSession().then((refreshed) => {
        if (refreshed) {
          setUser(refreshed);
          setIsSessionDead(false);
        } else {
          setUser(null);
          setIsSessionDead(true);
        }
      });
    };
    window.addEventListener("medops:session-refreshed", handler);
    return () => window.removeEventListener("medops:session-refreshed", handler);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: user !== null, isLoading, isSessionDead, login, registerPatient, registerDoctor, logout }),
    [user, isLoading, isSessionDead, login, registerPatient, registerDoctor, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
