import { useState, type FormEvent } from 'react';
import { ApiError, type ApiClient } from '../api/client';
import { disconnectDeepSeek, saveDeepSeekApiKey } from '../api/endpoints';
import type { DeepSeekStatusResponse } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';

const CONFLICT_MESSAGE = 'Your profile changed elsewhere. Reload the page and try again.';

interface FieldErrors {
  apiKey?: string;
}

/** Same convention as ChangePasswordCard/ProfileForm/RegisterPage/WorkoutForm: details keyed
 *  by the backend's bean-validation property name map one-for-one onto these fields. */
function splitDetails(details: Record<string, string>): { errors: FieldErrors; banner: string | null } {
  const errors: FieldErrors = {};
  const unmatched: string[] = [];
  for (const [field, message] of Object.entries(details)) {
    if (field === 'apiKey') {
      errors.apiKey = message;
    } else {
      unmatched.push(message);
    }
  }
  return { errors, banner: unmatched.length === 0 ? null : unmatched.join(' ') };
}

interface AiSettingsCardProps {
  api: ApiClient;
  /** Owned by the parent (its own fetch, like Google Health's status feeding FreshnessCard) —
   *  null while loading, so this card doesn't have to guess a value in the meantime. */
  status: DeepSeekStatusResponse | null;
  /** Fires after a successful save or removal, so the parent can refetch the status. */
  onChanged(): void;
}

/**
 * Lets the user supply their own DeepSeek API key so AI digests/insights can use DeepSeek
 * instead of the shared local Ollama instance (`app.ai.provider=deepseek`). The key is sent once,
 * over HTTPS, straight to the backend, which encrypts it before storage (TokenCipher, the same
 * scheme used for Google Health tokens) — it is never echoed back, so this component never has
 * the plaintext value once the page reloads.
 *
 * Save and Remove are two distinct actions against two distinct endpoints (not one "save a blank
 * field to clear" button) so an accidental submit with an empty field can never silently wipe a
 * working key, and so the confirmation banner is always tied to the call that actually succeeded
 * rather than a separately-passed label that could drift from it.
 */
export function AiSettingsCard({ api, status, onChanged }: AiSettingsCardProps) {
  const [apiKey, setApiKey] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [banner, setBanner] = useState<string | null>(null);
  const [lastAction, setLastAction] = useState<'saved' | 'removed' | null>(null);
  const [busy, setBusy] = useState(false);

  function reportError(cause: unknown): void {
    if (cause instanceof ApiError && cause.status === 400 && cause.body?.details !== undefined) {
      const split = splitDetails(cause.body.details);
      setErrors(split.errors);
      setBanner(split.banner);
    } else if (cause instanceof ApiError && cause.status === 409) {
      setBanner(CONFLICT_MESSAGE);
    } else {
      setBanner(messageFor(cause));
    }
  }

  async function handleSubmit(event: FormEvent): Promise<void> {
    event.preventDefault();
    if (apiKey.trim() === '') {
      return;
    }
    setErrors({});
    setBanner(null);
    setLastAction(null);
    setBusy(true);
    try {
      await saveDeepSeekApiKey(api, apiKey);
      setApiKey('');
      setLastAction('saved');
      onChanged();
    } catch (cause: unknown) {
      reportError(cause);
    } finally {
      setBusy(false);
    }
  }

  async function handleRemove(): Promise<void> {
    setErrors({});
    setBanner(null);
    setLastAction(null);
    setBusy(true);
    try {
      await disconnectDeepSeek(api);
      setLastAction('removed');
      onChanged();
    } catch (cause: unknown) {
      reportError(cause);
    } finally {
      setBusy(false);
    }
  }

  const configured = status?.connected ?? false;

  return (
    <Card title="AI settings">
      <p className="empty-note">
        {configured
          ? 'A DeepSeek API key is on file. Paste a new one below to replace it.'
          : 'Add your own DeepSeek API key to generate AI digests and insights through DeepSeek instead of the shared local model.'}
      </p>

      <form onSubmit={(event) => void handleSubmit(event)} noValidate>
        {banner !== null && <ErrorNote message={banner} />}
        {lastAction === 'saved' && banner === null && <p className="saved-note">Saved.</p>}
        {lastAction === 'removed' && banner === null && <p className="saved-note">Key removed.</p>}

        <div className="field">
          <label className="field-label" htmlFor="deepseekApiKey">
            DeepSeek API key
          </label>
          <input
            id="deepseekApiKey"
            type="password"
            autoComplete="off"
            placeholder={configured ? '••••••••••••••••' : 'sk-...'}
            value={apiKey}
            onChange={(event) => setApiKey(event.target.value)}
            aria-invalid={errors.apiKey !== undefined}
            aria-describedby={errors.apiKey !== undefined ? 'deepseekApiKey-error' : undefined}
          />
          {errors.apiKey !== undefined && (
            <span id="deepseekApiKey-error" className="field-error" role="alert">
              {errors.apiKey}
            </span>
          )}
        </div>

        <button type="submit" className="primary-button" disabled={busy || apiKey.trim() === ''}>
          {busy ? 'Saving…' : 'Save'}
        </button>
        {configured && (
          <button type="button" className="link-button" disabled={busy} onClick={() => void handleRemove()}>
            Remove key
          </button>
        )}
      </form>
    </Card>
  );
}
