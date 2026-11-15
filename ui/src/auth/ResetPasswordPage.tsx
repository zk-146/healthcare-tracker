import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { messageFor } from '../lib/apiMessage';

interface ResetPasswordPageProps {
  /** The token from the emailed link. */
  token: string;
  onSubmit(token: string, newPassword: string): Promise<void>;
  /** Leaves this screen for sign-in, whether the reset succeeded or was abandoned. */
  onDone(): void;
}

export function ResetPasswordPage({ token, onSubmit, onDone }: ResetPasswordPageProps) {
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [banner, setBanner] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFieldError(null);
    setBanner(null);

    if (newPassword !== confirmPassword) {
      setFieldError('Passwords do not match');
      return;
    }

    setBusy(true);
    try {
      await onSubmit(token, newPassword);
      setDone(true);
    } catch (cause) {
      const details = cause instanceof ApiError && cause.status === 400 ? cause.body?.details : undefined;
      if (details?.newPassword !== undefined) {
        setFieldError(details.newPassword);
      } else {
        // Includes the "link is invalid or has expired" 400, whose message is the
        // right thing to show as-is.
        setBanner(messageFor(cause));
      }
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <main className="login">
        <h1>Password changed</h1>
        <p className="login-note" role="status">
          Your password has been changed and every other session has been signed out.
        </p>
        <form>
          <button type="button" onClick={onDone}>
            Sign in
          </button>
        </form>
      </main>
    );
  }

  return (
    <main className="login">
      <h1>Choose a new password</h1>
      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="newPassword">New password</label>
        <input
          id="newPassword"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          aria-invalid={fieldError !== null}
          aria-describedby={fieldError !== null ? 'newPassword-error' : undefined}
          required
        />
        {fieldError !== null && (
          <span id="newPassword-error" className="field-error" role="alert">
            {fieldError}
          </span>
        )}

        <label htmlFor="confirmNewPassword">Confirm new password</label>
        <input
          id="confirmNewPassword"
          type="password"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          required
        />

        {banner !== null && (
          <p role="alert" className="error">
            {banner}
          </p>
        )}

        <button type="submit" disabled={busy}>
          {busy ? 'Saving…' : 'Set new password'}
        </button>

        <button type="button" className="link-button" onClick={onDone} disabled={busy}>
          Back to sign in
        </button>
      </form>
    </main>
  );
}
