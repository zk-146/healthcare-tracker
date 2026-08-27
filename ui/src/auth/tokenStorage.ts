import type { TokenStore, Tokens } from '../api/client';

const STORAGE_KEY = 'activity-tracker.tokens';

/**
 * localStorage rather than sessionStorage: on a phone the OS reclaims background tabs,
 * and sessionStorage would force a re-login every morning. httpOnly cookies would be
 * safer but the API is Bearer-only and CSRF is disabled because no cookies are used
 * (SecurityConfig.java:36), so cookie auth is a backend change, not a frontend one.
 * Mitigated by the strict CSP. Revisit if this is ever exposed beyond a private network.
 */
export const localTokenStore: TokenStore = {
  get(): Tokens | null {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw === null) {
      return null;
    }
    try {
      const parsed = JSON.parse(raw) as Partial<Tokens>;
      if (typeof parsed.token !== 'string' || typeof parsed.refreshToken !== 'string') {
        return null;
      }
      return { token: parsed.token, refreshToken: parsed.refreshToken };
    } catch {
      return null;
    }
  },
  set(tokens: Tokens): void {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
  },
  clear(): void {
    window.localStorage.removeItem(STORAGE_KEY);
  },
};
