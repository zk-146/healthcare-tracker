import { useState, type FormEvent, type ReactNode } from 'react';
import { ApiError, type ApiClient } from '../api/client';
import { deleteAccount, getProfile, updateProfile } from '../api/endpoints';
import type { ProfileResponse } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import { useLoadable } from '../lib/useLoadable';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';
import { ChangePasswordCard } from './ChangePasswordCard';
import { draftFrom, validateDraft, type FieldErrors, type ProfileDraft } from './validate';

const CONFLICT_MESSAGE = 'Your profile changed elsewhere. Reload the page and try again.';

const DRAFT_KEYS: readonly (keyof ProfileDraft)[] = [
  'fullName',
  'dateOfBirth',
  'gender',
  'heightCm',
  'weightKg',
];

/** Same convention as WorkoutForm: details keyed by field name map one-for-one. */
function splitDetails(details: Record<string, string>): { errors: FieldErrors; banner: string | null } {
  const errors: FieldErrors = {};
  const unmatched: string[] = [];
  for (const [field, message] of Object.entries(details)) {
    if ((DRAFT_KEYS as readonly string[]).includes(field)) {
      errors[field as keyof ProfileDraft] = message;
    } else {
      unmatched.push(message);
    }
  }
  return { errors, banner: unmatched.length === 0 ? null : unmatched.join(' ') };
}

interface FieldProps {
  id: string;
  label: string;
  error?: string;
  children: ReactNode;
}

function Field({ id, label, error, children }: FieldProps) {
  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        {label}
      </label>
      {children}
      {error !== undefined && (
        <span id={`${id}-error`} className="field-error" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}

interface ProfileFormProps {
  api: ApiClient;
  profile: ProfileResponse;
  onDeleted(): void;
  onPasswordChanged(): void;
  /** Injectable for tests; defaults to now. */
  now?: Date;
}

function ProfileForm({ api, profile, onDeleted, onPasswordChanged, now = new Date() }: ProfileFormProps) {
  const [current, setCurrent] = useState(profile);
  const [draft, setDraft] = useState<ProfileDraft>(() => draftFrom(profile));
  const [errors, setErrors] = useState<FieldErrors>({});
  const [banner, setBanner] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  function set<K extends keyof ProfileDraft>(key: K, value: ProfileDraft[K]): void {
    setDraft((draft) => ({ ...draft, [key]: value }));
    setSaved(false);
  }

  async function handleSubmit(event: FormEvent): Promise<void> {
    event.preventDefault();
    const { errors: found, input } = validateDraft(draft, now, draftFrom(current));
    setErrors(found);
    setBanner(null);
    setSaved(false);
    if (input === null) {
      return;
    }

    setBusy(true);
    try {
      const updated = await updateProfile(api, input);
      setCurrent(updated);
      setDraft(draftFrom(updated));
      setSaved(true);
    } catch (cause: unknown) {
      if (cause instanceof ApiError && cause.status === 400 && cause.body?.details !== undefined) {
        const split = splitDetails(cause.body.details);
        setErrors(split.errors);
        setBanner(split.banner);
      } else if (cause instanceof ApiError && cause.status === 409) {
        setBanner(CONFLICT_MESSAGE);
      } else {
        setBanner(messageFor(cause));
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(): Promise<void> {
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteAccount(api);
      onDeleted();
    } catch (cause: unknown) {
      setDeleteError(messageFor(cause));
      setConfirmingDelete(false);
      setDeleting(false);
    }
  }

  return (
    <>
      <Card title="Profile">
        <form onSubmit={(event) => void handleSubmit(event)} noValidate>
          <p className="profile-email">{current.email}</p>

          {banner !== null && <ErrorNote message={banner} />}
          {saved && banner === null && <p className="saved-note">Saved.</p>}

          <Field id="fullName" label="Full name" error={errors.fullName}>
            <input
              id="fullName"
              type="text"
              value={draft.fullName}
              onChange={(event) => set('fullName', event.target.value)}
              aria-invalid={errors.fullName !== undefined}
              aria-describedby={errors.fullName !== undefined ? 'fullName-error' : undefined}
            />
          </Field>

          <Field id="dateOfBirth" label="Date of birth" error={errors.dateOfBirth}>
            <input
              id="dateOfBirth"
              type="date"
              value={draft.dateOfBirth}
              onChange={(event) => set('dateOfBirth', event.target.value)}
              aria-invalid={errors.dateOfBirth !== undefined}
              aria-describedby={errors.dateOfBirth !== undefined ? 'dateOfBirth-error' : undefined}
            />
          </Field>

          <Field id="gender" label="Gender" error={errors.gender}>
            <input
              id="gender"
              type="text"
              value={draft.gender}
              onChange={(event) => set('gender', event.target.value)}
              aria-invalid={errors.gender !== undefined}
              aria-describedby={errors.gender !== undefined ? 'gender-error' : undefined}
            />
          </Field>

          <Field id="heightCm" label="Height (cm)" error={errors.heightCm}>
            <input
              id="heightCm"
              type="number"
              step="0.1"
              value={draft.heightCm}
              onChange={(event) => set('heightCm', event.target.value)}
              aria-invalid={errors.heightCm !== undefined}
              aria-describedby={errors.heightCm !== undefined ? 'heightCm-error' : undefined}
            />
          </Field>

          <Field id="weightKg" label="Weight (kg)" error={errors.weightKg}>
            <input
              id="weightKg"
              type="number"
              step="0.1"
              value={draft.weightKg}
              onChange={(event) => set('weightKg', event.target.value)}
              aria-invalid={errors.weightKg !== undefined}
              aria-describedby={errors.weightKg !== undefined ? 'weightKg-error' : undefined}
            />
          </Field>

          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Saving…' : 'Save changes'}
          </button>
        </form>
      </Card>

      <ChangePasswordCard api={api} onChanged={onPasswordChanged} />

      <Card title="Delete account">
        <p className="empty-note">
          Permanently deletes your account and all your workouts. This cannot be undone.
        </p>

        {deleteError !== null && <ErrorNote message={deleteError} />}

        {!confirmingDelete && (
          <button type="button" className="danger-button" onClick={() => setConfirmingDelete(true)}>
            Delete my account
          </button>
        )}

        {confirmingDelete && (
          <div className="confirm-row">
            <span>Delete your account and all your data?</span>
            <button
              type="button"
              className="danger-button"
              disabled={deleting}
              onClick={() => void handleDelete()}
            >
              {deleting ? 'Deleting…' : 'Confirm'}
            </button>
            <button
              type="button"
              className="link-button"
              disabled={deleting}
              onClick={() => setConfirmingDelete(false)}
            >
              Cancel
            </button>
          </div>
        )}
      </Card>
    </>
  );
}

interface ProfilePageProps {
  api: ApiClient;
  onAccountDeleted(): void;
  onPasswordChanged(): void;
}

export function ProfilePage({ api, onAccountDeleted, onPasswordChanged }: ProfilePageProps) {
  const profile = useLoadable<ProfileResponse>(() => getProfile(api), [api]);

  return (
    <>
      {profile.state === 'loading' && <Skeleton height={320} />}
      {profile.state === 'error' && <ErrorNote message={profile.message} />}
      {profile.state === 'ready' && (
        <ProfileForm
          api={api}
          profile={profile.value}
          onDeleted={onAccountDeleted}
          onPasswordChanged={onPasswordChanged}
        />
      )}
    </>
  );
}
