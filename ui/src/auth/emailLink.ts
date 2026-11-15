/**
 * Links in account emails carry their token in the URL fragment:
 *
 *   https://host/#reset-password=<token>
 *   https://host/#verify-email=<token>
 *
 * A fragment is never sent to the server, so the token stays out of request logs and
 * Referer headers; this module is the only place that reads it back out.
 */
export type EmailLinkKind = 'reset-password' | 'verify-email';

export interface EmailLink {
  kind: EmailLinkKind;
  token: string;
}

const KINDS: readonly EmailLinkKind[] = ['reset-password', 'verify-email'];

/** Tokens are base64url. Anything else in the fragment is not one of our links. */
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{16,200}$/;

export function parseEmailLink(hash: string): EmailLink | null {
  const fragment = hash.startsWith('#') ? hash.slice(1) : hash;
  const separator = fragment.indexOf('=');
  if (separator === -1) {
    return null;
  }
  const kind = fragment.slice(0, separator);
  const token = fragment.slice(separator + 1);
  if (!(KINDS as readonly string[]).includes(kind) || !TOKEN_PATTERN.test(token)) {
    return null;
  }
  return { kind: kind as EmailLinkKind, token };
}

/**
 * Drops the fragment from the address bar without adding a history entry, so the token
 * does not linger in the URL (or come back on reload) once the link has been dealt with.
 */
export function clearEmailLink(): void {
  window.history.replaceState(null, '', window.location.pathname + window.location.search);
}
