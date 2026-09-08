import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { ApiError, type ApiClient } from '../api/client';
import { createActivity, deleteActivity, updateActivity } from '../api/endpoints';
import type { ActivityResponse, ActivityType } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import { ErrorNote } from '../ui/ErrorNote';
import {
  draftFrom,
  emptyDraft,
  hasDetails,
  validateDraft,
  type FieldErrors,
  type WorkoutDraft,
} from './validate';

export const TYPE_LABELS: Record<ActivityType, string> = {
  WALKING: 'Walking',
  RUNNING: 'Running',
  YOGA: 'Yoga',
  CYCLING: 'Cycling',
  SWIMMING: 'Swimming',
  STRENGTH_TRAINING: 'Strength training',
  STRETCHING: 'Stretching',
  OTHER: 'Other',
};

const CONFLICT_MESSAGE =
  'This workout changed on another device. Close and reopen to see the latest.';

const NOTES_LIMIT = 1000;

const DRAFT_KEYS: readonly string[] = [
  'activityType',
  'startedAt',
  'durationMinutes',
  'distanceKm',
  'caloriesBurned',
  'steps',
  'heartRateAvg',
  'notes',
];

const FOCUSABLE = 'select, input, textarea, button';

/**
 * GlobalExceptionHandler keys `details` by the bean-validation property name, which
 * matches our draft keys one-for-one. Anything unrecognised (a class-level rule such
 * as @ValidDateRange) has no field to attach to, so it becomes banner text.
 */
function splitDetails(details: Record<string, string>): { errors: FieldErrors; banner: string | null } {
  const errors: FieldErrors = {};
  const unmatched: string[] = [];
  for (const [field, message] of Object.entries(details)) {
    if (DRAFT_KEYS.includes(field)) {
      errors[field as keyof WorkoutDraft] = message;
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
        <span className="field-error" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}

interface WorkoutFormProps {
  api: ApiClient;
  /** Present in edit mode, absent in create mode. */
  initial?: ActivityResponse;
  onClose(): void;
  /** Fires before onClose on every successful create, update or delete. */
  onSaved(): void;
  /** Injectable for tests; defaults to now. */
  now?: Date;
}

export function WorkoutForm({ api, initial, onClose, onSaved, now = new Date() }: WorkoutFormProps) {
  const [draft, setDraft] = useState<WorkoutDraft>(() =>
    initial === undefined ? emptyDraft(now) : draftFrom(initial),
  );
  const [errors, setErrors] = useState<FieldErrors>({});
  const [banner, setBanner] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [showDetails, setShowDetails] = useState(
    () => initial !== undefined && hasDetails(draftFrom(initial)),
  );
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    dialogRef.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus();
  }, []);

  function set<K extends keyof WorkoutDraft>(key: K, value: WorkoutDraft[K]): void {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  /** Esc closes; Tab wraps, so focus never escapes the overlay to the page behind it. */
  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>): void {
    if (event.key === 'Escape') {
      onClose();
      return;
    }
    if (event.key !== 'Tab' || dialogRef.current === null) {
      return;
    }
    const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
      (element) => !element.hasAttribute('disabled'),
    );
    if (focusable.length === 0) {
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    } else if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    }
  }

  function handleFailure(cause: unknown): void {
    if (cause instanceof ApiError && cause.status === 400 && cause.body?.details !== undefined) {
      const split = splitDetails(cause.body.details);
      setErrors(split.errors);
      setBanner(split.banner);
      return;
    }
    if (cause instanceof ApiError && cause.status === 409 && initial !== undefined) {
      setBanner(CONFLICT_MESSAGE);
      return;
    }
    setBanner(messageFor(cause));
  }

  async function handleSubmit(event: FormEvent): Promise<void> {
    event.preventDefault();
    const { errors: found, input } = validateDraft(draft, now);
    setErrors(found);
    setBanner(null);
    if (input === null) {
      return;
    }

    setBusy(true);
    try {
      if (initial === undefined) {
        await createActivity(api, input);
      } else {
        await updateActivity(api, initial.id, input);
      }
      onSaved();
      onClose();
    } catch (cause: unknown) {
      handleFailure(cause);
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete(): Promise<void> {
    if (initial === undefined) {
      return;
    }
    setBusy(true);
    setBanner(null);
    try {
      await deleteActivity(api, initial.id);
      onSaved();
      onClose();
    } catch (cause: unknown) {
      setBanner(messageFor(cause));
      setConfirmingDelete(false);
    } finally {
      setBusy(false);
    }
  }

  const title = initial === undefined ? 'Log workout' : 'Edit workout';

  return (
    <div className="overlay" onKeyDown={handleKeyDown}>
      <div className="overlay-panel" role="dialog" aria-modal="true" aria-label={title} ref={dialogRef}>
        <form onSubmit={(event) => void handleSubmit(event)} noValidate>
          <header className="overlay-header">
            <h2 className="overlay-title">{title}</h2>
            <button type="button" className="link-button" onClick={onClose}>
              Close
            </button>
          </header>

          {banner !== null && <ErrorNote message={banner} />}

          <Field id="activityType" label="Activity" error={errors.activityType}>
            <select
              id="activityType"
              value={draft.activityType}
              onChange={(event) => set('activityType', event.target.value as ActivityType)}
            >
              {(Object.keys(TYPE_LABELS) as ActivityType[]).map((type) => (
                <option key={type} value={type}>
                  {TYPE_LABELS[type]}
                </option>
              ))}
            </select>
          </Field>

          <Field id="startedAt" label="Started at" error={errors.startedAt}>
            <input
              id="startedAt"
              type="datetime-local"
              value={draft.startedAt}
              onChange={(event) => set('startedAt', event.target.value)}
            />
          </Field>

          <Field id="durationMinutes" label="Duration (minutes)" error={errors.durationMinutes}>
            <input
              id="durationMinutes"
              type="number"
              inputMode="numeric"
              value={draft.durationMinutes}
              onChange={(event) => set('durationMinutes', event.target.value)}
            />
          </Field>

          {!showDetails && (
            <button type="button" className="link-button" onClick={() => setShowDetails(true)}>
              More details
            </button>
          )}

          {showDetails && (
            <>
              <Field id="distanceKm" label="Distance (km)" error={errors.distanceKm}>
                <input
                  id="distanceKm"
                  type="number"
                  step="0.01"
                  value={draft.distanceKm}
                  onChange={(event) => set('distanceKm', event.target.value)}
                />
              </Field>

              <Field id="caloriesBurned" label="Calories burned" error={errors.caloriesBurned}>
                <input
                  id="caloriesBurned"
                  type="number"
                  value={draft.caloriesBurned}
                  onChange={(event) => set('caloriesBurned', event.target.value)}
                />
              </Field>

              <Field id="steps" label="Steps" error={errors.steps}>
                <input
                  id="steps"
                  type="number"
                  value={draft.steps}
                  onChange={(event) => set('steps', event.target.value)}
                />
              </Field>

              <Field id="heartRateAvg" label="Average heart rate (bpm)" error={errors.heartRateAvg}>
                <input
                  id="heartRateAvg"
                  type="number"
                  value={draft.heartRateAvg}
                  onChange={(event) => set('heartRateAvg', event.target.value)}
                />
              </Field>

              <Field id="notes" label="Notes" error={errors.notes}>
                <textarea
                  id="notes"
                  rows={3}
                  value={draft.notes}
                  onChange={(event) => set('notes', event.target.value)}
                />
                <span className="field-counter">
                  {draft.notes.length} / {NOTES_LIMIT}
                </span>
              </Field>
            </>
          )}

          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Saving…' : 'Save workout'}
          </button>

          {initial !== undefined && !confirmingDelete && (
            <button
              type="button"
              className="danger-button"
              onClick={() => setConfirmingDelete(true)}
            >
              Delete this workout
            </button>
          )}

          {initial !== undefined && confirmingDelete && (
            <div className="confirm-row">
              <span>Delete this workout?</span>
              <button
                type="button"
                className="danger-button"
                disabled={busy}
                onClick={() => void handleDelete()}
              >
                Confirm
              </button>
              <button type="button" className="link-button" onClick={() => setConfirmingDelete(false)}>
                Cancel
              </button>
            </div>
          )}
        </form>
      </div>
    </div>
  );
}
