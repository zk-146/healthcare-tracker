import { useState, type FormEvent } from 'react';
import { messageFor } from '../lib/apiMessage';

interface ForgotPasswordPageProps {
  onSubmit(email: string): Promise<void>;
  onBackToLogin(): void;
}

export function ForgotPasswordPage({ onSubmit, onBackToLogin }: ForgotPasswordPageProps) {
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await onSubmit(email);
      setSent(true);
    } catch (cause) {
      setError(messageFor(cause));
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <main className="login">
        <h1>Check your email</h1>
        {/* Worded for both cases on purpose: the server does not say whether the address
            has an account, and neither should this screen. */}
        <p className="login-note" role="status">
          If an account exists for {email}, we have sent a link to reset its password. The link
          works for 30 minutes.
        </p>
        <form>
          <button type="button" onClick={onBackToLogin}>
            Back to sign in
          </button>
        </form>
      </main>
    );
  }

  return (
    <main className="login">
      <h1>Reset password</h1>
      <p className="login-note">Enter your email and we will send you a link to choose a new password.</p>
      <form onSubmit={handleSubmit}>
        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />

        {error !== null && (
          <p role="alert" className="error">
            {error}
          </p>
        )}

        <button type="submit" disabled={busy}>
          {busy ? 'Sending…' : 'Send reset link'}
        </button>

        <button type="button" className="link-button" onClick={onBackToLogin} disabled={busy}>
          Back to sign in
        </button>
      </form>
    </main>
  );
}
