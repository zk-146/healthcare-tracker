import { useState, type FormEvent } from 'react';
import { ApiError, type ApiClient } from '../api/client';
import { changePassword } from '../api/endpoints';
import { messageFor } from '../lib/apiMessage';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';

interface FieldErrors {
  currentPassword?: string;
  newPassword?: string;
}

const FIELD_KEYS: readonly (keyof FieldErrors)[] = ['currentPassword', 'newPassword'];

/** Same convention as WorkoutForm/ProfileForm/RegisterPage: details keyed by the
 *  backend's bean-validation property name map one-for-one onto these fields. */
function splitDetails(details: Record<string, string>): { errors: FieldErrors; banner: string | null } {
  const errors: FieldErrors = {};
  const unmatched: string[] = [];
  for (const [field, message] of Object.entries(details)) {
    if ((FIELD_KEYS as readonly string[]).includes(field)) {
      errors[field as keyof FieldErrors] = message;
    } else {
      unmatched.push(message);
    }
  }
  return { errors, banner: unmatched.length === 0 ? null : unmatched.join(' ') };
}

interface ChangePasswordCardProps {
  api: ApiClient;
  /**
   * Fires after a successful change. The backend revokes every refresh token on a
   * password change, so the caller is expected to sign the user out here — the
   * current access token stays valid until it naturally expires, but there is no
   * reason to let the old session linger past a deliberate password change.
   */
  onChanged(): void;
}

export function ChangePasswordCard({ api, onChanged }: ChangePasswordCardProps) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [banner, setBanner] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent): Promise<void> {
    event.preventDefault();
    setErrors({});
    setBanner(null);

    if (newPassword !== confirmPassword) {
      setErrors({ newPassword: 'Passwords do not match' });
      return;
    }

    setBusy(true);
    try {
      await changePassword(api, currentPassword, newPassword);
      onChanged();
    } catch (cause: unknown) {
      if (cause instanceof ApiError && cause.status === 400 && cause.body?.details !== undefined) {
        const split = splitDetails(cause.body.details);
        setErrors(split.errors);
        setBanner(split.banner);
      } else {
        // Covers the 401 "Current password is incorrect" case too — the backend's
        // message is already the right thing to show, and messageFor surfaces it.
        setBanner(messageFor(cause));
      }
      setBusy(false);
    }
  }

  return (
    <Card title="Change password">
      <form onSubmit={(event) => void handleSubmit(event)} noValidate>
        {banner !== null && <ErrorNote message={banner} />}

        <div className="field">
          <label className="field-label" htmlFor="currentPassword">
            Current password
          </label>
          <input
            id="currentPassword"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            aria-invalid={errors.currentPassword !== undefined}
            aria-describedby={errors.currentPassword !== undefined ? 'currentPassword-error' : undefined}
          />
          {errors.currentPassword !== undefined && (
            <span id="currentPassword-error" className="field-error" role="alert">
              {errors.currentPassword}
            </span>
          )}
        </div>

        <div className="field">
          <label className="field-label" htmlFor="newPassword">
            New password
          </label>
          <input
            id="newPassword"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            aria-invalid={errors.newPassword !== undefined}
            aria-describedby={errors.newPassword !== undefined ? 'newPassword-error' : undefined}
          />
          {errors.newPassword !== undefined && (
            <span id="newPassword-error" className="field-error" role="alert">
              {errors.newPassword}
            </span>
          )}
        </div>

        <div className="field">
          <label className="field-label" htmlFor="confirmNewPassword">
            Confirm new password
          </label>
          <input
            id="confirmNewPassword"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
          />
        </div>

        <button type="submit" className="primary-button" disabled={busy}>
          {busy ? 'Changing…' : 'Change password'}
        </button>
      </form>
    </Card>
  );
}
