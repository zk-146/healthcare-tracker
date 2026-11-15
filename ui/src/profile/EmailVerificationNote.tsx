import { useState } from 'react';
import type { ApiClient } from '../api/client';
import { resendVerification } from '../api/endpoints';
import { messageFor } from '../lib/apiMessage';

interface EmailVerificationNoteProps {
  api: ApiClient;
  verified: boolean;
}

/**
 * Shown under the address on the Profile tab. An unverified account works normally —
 * verification is not a condition of signing in — so this is a prompt, not a blocker.
 */
export function EmailVerificationNote({ api, verified }: EmailVerificationNoteProps) {
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (verified) {
    return <p className="email-status">Email confirmed</p>;
  }

  async function handleResend(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      await resendVerification(api);
      setSent(true);
    } catch (cause: unknown) {
      setError(messageFor(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="email-status">
      {sent ? (
        <p role="status">Confirmation email sent. Check your inbox for the link.</p>
      ) : (
        <p>
          Email not confirmed yet.{' '}
          <button type="button" className="link-button" onClick={() => void handleResend()} disabled={busy}>
            {busy ? 'Sending…' : 'Send confirmation email'}
          </button>
        </p>
      )}
      {error !== null && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
