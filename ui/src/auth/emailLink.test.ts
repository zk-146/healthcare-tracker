import { afterEach, describe, expect, it } from 'vitest';
import { clearEmailLink, parseEmailLink } from './emailLink';

const TOKEN = 'abcDEF123_-abcDEF123_-abcDEF123_-abcDEF1234';

describe('parseEmailLink', () => {
  it('reads a password-reset link', () => {
    expect(parseEmailLink(`#reset-password=${TOKEN}`)).toEqual({
      kind: 'reset-password',
      token: TOKEN,
    });
  });

  it('reads an email-verification link', () => {
    expect(parseEmailLink(`#verify-email=${TOKEN}`)).toEqual({ kind: 'verify-email', token: TOKEN });
  });

  it.each(['', '#', '#profile', `#something-else=${TOKEN}`, '#reset-password=', '#reset-password=short'])(
    'ignores %j',
    (hash) => {
      expect(parseEmailLink(hash)).toBeNull();
    },
  );

  it('ignores a token containing characters outside base64url', () => {
    expect(parseEmailLink(`#reset-password=${TOKEN}<script>`)).toBeNull();
  });
});

describe('clearEmailLink', () => {
  afterEach(() => {
    window.history.replaceState(null, '', '/');
  });

  it('removes the fragment but keeps the path', () => {
    window.history.replaceState(null, '', `/?x=1#reset-password=${TOKEN}`);

    clearEmailLink();

    expect(window.location.hash).toBe('');
    expect(window.location.pathname + window.location.search).toBe('/?x=1');
  });
});
