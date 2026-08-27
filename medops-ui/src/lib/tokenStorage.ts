import type { AuthTokens } from "../types/auth";

// Storing raw JWTs in localStorage is convenient for a learning-stage SPA but
// is readable by any script on the page (XSS risk). A production version of
// this would move refresh tokens to an httpOnly cookie set by the backend.
export const TOKENS_STORAGE_KEY = "medops.tokens";

type TokensListener = (tokens: AuthTokens | null) => void;

// The axios interceptors refresh and clear tokens outside of React's control, so
// AuthContext subscribes here instead of owning the tokens itself. That keeps
// "signed in?" answerable from one place no matter who changed the session.
const listeners = new Set<TokensListener>();

export function readStoredTokens(): AuthTokens | null {
  const raw = localStorage.getItem(TOKENS_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthTokens;
  } catch {
    return null;
  }
}

export function writeStoredTokens(tokens: AuthTokens): void {
  localStorage.setItem(TOKENS_STORAGE_KEY, JSON.stringify(tokens));
  notify(tokens);
}

export function clearStoredTokens(): void {
  localStorage.removeItem(TOKENS_STORAGE_KEY);
  notify(null);
}

export function subscribeToStoredTokens(listener: TokensListener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function notify(tokens: AuthTokens | null): void {
  for (const listener of listeners) {
    listener(tokens);
  }
}
