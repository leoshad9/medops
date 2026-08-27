import type { AuthUser, Role } from "../types/auth";

interface AccessTokenClaims {
  sub: string;
  roles: string[];
  exp: number;
}

const KNOWN_ROLES: readonly Role[] = ["DOCTOR", "PATIENT"];

function decodeBase64Url(segment: string): string {
  const base64 = segment.replaceAll("-", "+").replaceAll("_", "/");
  const padding = (4 - (base64.length % 4)) % 4;
  return atob(base64 + "=".repeat(padding));
}

function parseClaims(accessToken: string): AccessTokenClaims | null {
  const payloadSegment = accessToken.split(".")[1];
  if (!payloadSegment) {
    return null;
  }

  try {
    return JSON.parse(decodeBase64Url(payloadSegment)) as AccessTokenClaims;
  } catch {
    return null;
  }
}

// The backend signs the JWT; we only decode it here to read claims for UI
// routing. Actual authorization still happens server-side on every request.
export function parseAuthUser(accessToken: string): AuthUser | null {
  const claims = parseClaims(accessToken);
  if (!claims) {
    return null;
  }

  const role = claims.roles
    .map((authority) => authority.replace(/^ROLE_/, ""))
    .find((name): name is Role => (KNOWN_ROLES as string[]).includes(name));

  return role ? { email: claims.sub, role } : null;
}

// Treat a token as expired slightly early so a request that is already in flight
// when the token lapses does not come back as an avoidable 401.
const EXPIRY_SKEW_SECONDS = 30;

export function isAccessTokenExpired(accessToken: string): boolean {
  const claims = parseClaims(accessToken);
  if (!claims?.exp) {
    return true;
  }
  return claims.exp - EXPIRY_SKEW_SECONDS <= Date.now() / 1000;
}
