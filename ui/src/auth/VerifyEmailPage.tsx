import { useState } from 'react';
import { messageFor } from '../lib/apiMessage';

interface VerifyEmailPageProps {
  /** The token from the emailed link. */
  token: string;
  onSubmit(token: string): Promise<void>;
  onDone(): void;
}

/**
 * Confirmation happens on a button press rather than on load. The token is single-use,
 * so anything that opens the link without the user meaning it — a mail scanner following
 * URLs, React StrictMode running an effect twice — would otherwise spend it, and the
 * user's own click would then be told the link is invalid.
 */
export function VerifyEmailPage({ token, onSubmit, onDone }: VerifyEmailPageProps) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [verified, setVerified] = useState(false);

  async function handleConfirm() {
    setError(null);
    setBusy(true);
    try {
      await onSubmit(token);
      setVerified(true);
    } catch (cause) {
      setError(messageFor(cause));
    } finally {
      setBusy(false);
    }
  }

  if (verified) {
    return (
      <main className="login">
        <h1>Email confirmed</h1>
        <p className="login-note" role="status">
          Thanks — your email address is confirmed.
        </p>
        <form>
          <button type="button" onClick={onDone}>
            Continue
          </button>
        </form>
      </main>
    );
  }

  return (
    <main className="login">
      <h1>Confirm your email</h1>
      <p className="login-note">Press the button below to confirm this email address for your account.</p>
      <form>
        {error !== null && (
          <p role="alert" className="error">
            {error}
          </p>
        )}

        <button type="button" onClick={() => void handleConfirm()} disabled={busy}>
          {busy ? 'Confirming…' : 'Confirm email address'}
        </button>

        <button type="button" className="link-button" onClick={onDone} disabled={busy}>
          Not now
        </button>
      </form>
    </main>
  );
}
