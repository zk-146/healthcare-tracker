import { useState, type FormEvent } from 'react';

interface LoginPageProps {
  onSubmit(email: string, password: string): Promise<void>;
  onSwitchToRegister(): void;
}

export function LoginPage({ onSubmit, onSwitchToRegister }: LoginPageProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await onSubmit(email, password);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Sign-in failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login">
      <h1>Activity</h1>
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

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        {error !== null && (
          <p role="alert" className="error">
            {error}
          </p>
        )}

        <button type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>

        <button type="button" className="link-button" onClick={onSwitchToRegister} disabled={busy}>
          New here? Create an account
        </button>
      </form>
    </main>
  );
}
