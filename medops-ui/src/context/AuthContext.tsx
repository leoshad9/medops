import { createContext, useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

import {
  login as loginRequest,
  logout as logoutRequest,
  registerDoctor as registerDoctorRequest,
  registerPatient as registerPatientRequest,
} from "../services/authService";
import { getSessionBootstrap } from "../services/sessionService";
import { prefetchCsrfToken } from "../services/api";
import { refreshSession } from "../services/sessionRefresh";
import type { AuthUser, RegisterDoctorRequest, RegisterPatientRequest } from "../types/auth";
import type { DoctorProfile } from "../types/doctor";
import type { PatientProfile } from "../types/patient";

export interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isSessionDead: boolean;
  /** Profile delivered by the single-request /v1/me bootstrap; null after login/registration. */
  patientProfile: PatientProfile | null;
  doctorProfile: DoctorProfile | null;
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
  const [patientProfile, setPatientProfile] = useState<PatientProfile | null>(null);
  const [doctorProfile, setDoctorProfile] = useState<DoctorProfile | null>(null);

  // Restore the session from the HttpOnly access-token cookie. The cookie is not
  // readable by JavaScript, so the server is the only source of truth for who is
  // signed in. /v1/me returns the identity and the role-specific profile in a single
  // request, so portal headers render without a second, serialized round trip.
  // The CSRF cookie is warmed in parallel (not awaited): the first future
  // state-changing request then finds XSRF-TOKEN already set and skips the
  // serialized GET /api/auth/csrf hop inside the axios request interceptor.
  useEffect(() => {
    let cancelled = false;
    void prefetchCsrfToken();
    getSessionBootstrap()
      .then((session) => {
        if (!cancelled) {
          setUser({ email: session.email, role: session.role });
          setPatientProfile(session.patientProfile);
          setDoctorProfile(session.doctorProfile);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setUser(null);
          setPatientProfile(null);
          setDoctorProfile(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Login/registration/refresh responses carry identity only — the /v1/me bootstrap is
  // the sole source of profiles — so accepting a new identity drops any profile
  // captured for a previously signed-in user.
  const adoptIdentity = useCallback((nextUser: AuthUser | null) => {
    setPatientProfile(null);
    setDoctorProfile(null);
    setUser(nextUser);
  }, []);

  const login = useCallback(async (email: string, password: string): Promise<AuthUser> => {
    const loggedInUser = await loginRequest({ email, password });
    adoptIdentity(loggedInUser);
    return loggedInUser;
  }, [adoptIdentity]);

  const registerPatient = useCallback(
    async (request: RegisterPatientRequest): Promise<AuthUser> => {
      await registerPatientRequest(request);
      return login(request.email, request.password);
    },
    [login],
  );

  const registerDoctor = useCallback(
    async (request: RegisterDoctorRequest): Promise<AuthUser> => {
      await registerDoctorRequest(request);
      return login(request.email, request.password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    try {
      await logoutRequest();
    } catch {
      // ignored — local session is cleared below regardless
    }
    adoptIdentity(null);
  }, [adoptIdentity]);

  // When the axios interceptor refreshes a 401, the new access token arrives as a
  // cookie, but the user identity may have changed (e.g. role update). Re-fetch
  // /auth/me so the UI reflects the current session. If refresh fails, the session
  // is genuinely over — flag it so the router can redirect to login.
  useEffect(() => {
    const handler = () => {
      void refreshSession().then((refreshed) => {
        if (refreshed) {
          adoptIdentity(refreshed);
          setIsSessionDead(false);
        } else {
          adoptIdentity(null);
          setIsSessionDead(true);
        }
      });
    };
    window.addEventListener("medops:session-refreshed", handler);
    return () => window.removeEventListener("medops:session-refreshed", handler);
  }, [adoptIdentity]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isLoading,
      isSessionDead,
      patientProfile,
      doctorProfile,
      login,
      registerPatient,
      registerDoctor,
      logout,
    }),
    [user, isLoading, isSessionDead, patientProfile, doctorProfile, login, registerPatient, registerDoctor, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
