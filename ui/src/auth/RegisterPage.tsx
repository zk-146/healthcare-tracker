import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { messageFor } from '../lib/apiMessage';

export interface RegisterFieldErrors {
  email?: string;
  password?: string;
  fullName?: string;
}

const FIELD_KEYS: readonly (keyof RegisterFieldErrors)[] = ['email', 'password', 'fullName'];

/**
 * RegisterRequest's bean-validation property names (email, password, fullName) match
 * these keys one-for-one — same convention WorkoutForm uses for `details`. Anything
 * unrecognised becomes banner text instead of being silently dropped.
 */
function splitDetails(details: Record<string, string>): {
  errors: RegisterFieldErrors;
  banner: string | null;
} {
  const errors: RegisterFieldErrors = {};
  const unmatched: string[] = [];
  for (const [field, message] of Object.entries(details)) {
    if ((FIELD_KEYS as readonly string[]).includes(field)) {
      errors[field as keyof RegisterFieldErrors] = message;
    } else {
      unmatched.push(message);
    }
  }
  return { errors, banner: unmatched.length === 0 ? null : unmatched.join(' ') };
}

interface RegisterPageProps {
  onSubmit(email: string, password: string, fullName: string): Promise<void>;
  onSwitchToLogin(): void;
}

export function RegisterPage({ onSubmit, onSwitchToLogin }: RegisterPageProps) {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState<RegisterFieldErrors>({});
  const [banner, setBanner] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setErrors({});
    setBanner(null);

    if (password !== confirmPassword) {
      setErrors({ password: 'Passwords do not match' });
      return;
    }

    setBusy(true);
    try {
      await onSubmit(email, password, fullName);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 400 && cause.body?.details !== undefined) {
        const split = splitDetails(cause.body.details);
        setErrors(split.errors);
        setBanner(split.banner);
      } else {
        setBanner(messageFor(cause));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login">
      <h1>Activity</h1>
      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="fullName">Full name</label>
        <input
          id="fullName"
          type="text"
          autoComplete="name"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          aria-invalid={errors.fullName !== undefined}
          aria-describedby={errors.fullName !== undefined ? 'fullName-error' : undefined}
          required
        />
        {errors.fullName !== undefined && (
          <span id="fullName-error" className="field-error" role="alert">
            {errors.fullName}
          </span>
        )}

        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          aria-invalid={errors.email !== undefined}
          aria-describedby={errors.email !== undefined ? 'email-error' : undefined}
          required
        />
        {errors.email !== undefined && (
          <span id="email-error" className="field-error" role="alert">
            {errors.email}
          </span>
        )}

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-invalid={errors.password !== undefined}
          aria-describedby={errors.password !== undefined ? 'password-error' : undefined}
          required
        />
        {errors.password !== undefined && (
          <span id="password-error" className="field-error" role="alert">
            {errors.password}
          </span>
        )}

        <label htmlFor="confirmPassword">Confirm password</label>
        <input
          id="confirmPassword"
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
          {busy ? 'Creating account…' : 'Create account'}
        </button>

        <button type="button" className="link-button" onClick={onSwitchToLogin} disabled={busy}>
          Already have an account? Sign in
        </button>
      </form>
    </main>
  );
}
