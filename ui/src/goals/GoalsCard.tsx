import { useState, type FormEvent } from 'react';
import { ApiError, type ApiClient } from '../api/client';
import { createGoal, deleteGoal, listGoals, updateGoalTarget } from '../api/endpoints';
import type { GoalMetric, GoalPeriod, GoalResponse } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import {
  GOAL_METRICS,
  GOAL_PERIODS,
  formatValue,
  goalTitle,
  metricLabel,
  metricUnit,
  periodLabel,
} from '../lib/goals';
import { useLoadable } from '../lib/useLoadable';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';
import { emptyDraft, targetError, validateDraft, type GoalDraft } from './validate';

const MAX_ACTIVE_GOALS = 10;

const CONFLICT_MESSAGE =
  'That goal changed elsewhere. Reload the page and try again.';

/**
 * Maps a 400 body onto the target field, a 409 onto a banner, anything else onto generic copy.
 * Same convention as AiSettingsCard and ProfileForm: `details` keys are backend field names.
 */
function describe(cause: unknown): { targetValue: string | null; banner: string | null } {
  if (cause instanceof ApiError && cause.status === 400 && cause.body?.details !== undefined) {
    const details = cause.body.details;
    const fieldMessage = details.targetValue ?? null;
    const rest = Object.entries(details)
      .filter(([field]) => field !== 'targetValue')
      .map(([, message]) => message);
    return {
      targetValue: fieldMessage,
      banner: rest.length === 0 ? null : rest.join(' '),
    };
  }
  // 409 covers both a duplicate metric/period pair and an optimistic-lock clash. The server's
  // own message distinguishes them and is more specific than anything written here, so prefer it.
  if (cause instanceof ApiError && cause.status === 409) {
    return { targetValue: null, banner: cause.body?.error ?? CONFLICT_MESSAGE };
  }
  return { targetValue: null, banner: messageFor(cause) };
}

interface GoalListItemProps {
  api: ApiClient;
  goal: GoalResponse;
  onChanged(): void;
}

function GoalListItem({ api, goal, onChanged }: GoalListItemProps) {
  const [editing, setEditing] = useState(false);
  const [target, setTarget] = useState(String(goal.targetValue));
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [banner, setBanner] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmingRemove, setConfirmingRemove] = useState(false);

  const errorId = `goal-${goal.id}-error`;

  function startEditing(): void {
    setTarget(String(goal.targetValue));
    setFieldError(null);
    setBanner(null);
    setEditing(true);
  }

  async function handleSave(event: FormEvent): Promise<void> {
    event.preventDefault();
    const local = targetError(target, goal.metric, goal.period);
    setFieldError(local);
    setBanner(null);
    if (local !== null) {
      return;
    }

    setBusy(true);
    try {
      await updateGoalTarget(api, goal.id, Number(target));
      setEditing(false);
      onChanged();
    } catch (cause: unknown) {
      const described = describe(cause);
      setFieldError(described.targetValue);
      setBanner(described.banner);
    } finally {
      setBusy(false);
    }
  }

  async function handleRemove(): Promise<void> {
    setBusy(true);
    setBanner(null);
    try {
      await deleteGoal(api, goal.id);
      onChanged();
    } catch (cause: unknown) {
      setBanner(describe(cause).banner);
      setConfirmingRemove(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="goal-manage-row">
      <div className="goal-manage-head">
        <span className="goal-row-title">{goalTitle(goal.metric, goal.period)}</span>
        {!editing && (
          <span className="goal-row-value">
            {formatValue(goal.targetValue, goal.metric)} {goal.unit}
          </span>
        )}
      </div>

      {banner !== null && <ErrorNote message={banner} />}

      {editing ? (
        <form onSubmit={(event) => void handleSave(event)} noValidate>
          <div className="field">
            <label className="field-label" htmlFor={`goal-${goal.id}-target`}>
              New target ({goal.unit})
            </label>
            <input
              id={`goal-${goal.id}-target`}
              type="number"
              step="any"
              value={target}
              onChange={(event) => setTarget(event.target.value)}
              aria-invalid={fieldError !== null}
              aria-describedby={fieldError !== null ? errorId : undefined}
            />
            {fieldError !== null && (
              <span id={errorId} className="field-error" role="alert">
                {fieldError}
              </span>
            )}
          </div>

          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Saving…' : 'Save target'}
          </button>
          <button
            type="button"
            className="link-button"
            disabled={busy}
            onClick={() => setEditing(false)}
          >
            Cancel
          </button>
        </form>
      ) : (
        <div className="confirm-row">
          <button type="button" className="link-button" disabled={busy} onClick={startEditing}>
            Change target
          </button>

          {!confirmingRemove && (
            <button
              type="button"
              className="link-button"
              disabled={busy}
              onClick={() => setConfirmingRemove(true)}
            >
              Remove
            </button>
          )}

          {confirmingRemove && (
            <>
              <span>Remove this goal?</span>
              <button
                type="button"
                className="danger-button"
                disabled={busy}
                onClick={() => void handleRemove()}
              >
                {busy ? 'Removing…' : 'Confirm'}
              </button>
              <button
                type="button"
                className="link-button"
                disabled={busy}
                onClick={() => setConfirmingRemove(false)}
              >
                Cancel
              </button>
            </>
          )}
        </div>
      )}
    </li>
  );
}

interface AddGoalFormProps {
  api: ApiClient;
  existing: GoalResponse[];
  onAdded(): void;
}

function AddGoalForm({ api, existing, onAdded }: AddGoalFormProps) {
  const [draft, setDraft] = useState<GoalDraft>(emptyDraft);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [banner, setBanner] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function set<K extends keyof GoalDraft>(key: K, value: GoalDraft[K]): void {
    setDraft((current) => ({ ...current, [key]: value }));
    setFieldError(null);
    setBanner(null);
  }

  // The backend answers a duplicate pair with a 409. Catching it here first turns an error
  // the user has to read into a disabled button they can see coming.
  const alreadyTaken = existing.some(
    (goal) => goal.metric === draft.metric && goal.period === draft.period,
  );

  async function handleSubmit(event: FormEvent): Promise<void> {
    event.preventDefault();
    const { errors, input } = validateDraft(draft);
    setFieldError(errors.targetValue ?? null);
    setBanner(null);
    if (input === null) {
      return;
    }

    setBusy(true);
    try {
      await createGoal(api, input);
      setDraft(emptyDraft());
      onAdded();
    } catch (cause: unknown) {
      const described = describe(cause);
      setFieldError(described.targetValue);
      setBanner(described.banner);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={(event) => void handleSubmit(event)} noValidate>
      {banner !== null && <ErrorNote message={banner} />}

      <div className="field">
        <label className="field-label" htmlFor="goalMetric">
          Measure
        </label>
        <select
          id="goalMetric"
          value={draft.metric}
          onChange={(event) => set('metric', event.target.value as GoalMetric)}
        >
          {GOAL_METRICS.map((metric) => (
            <option key={metric} value={metric}>
              {metricLabel(metric)}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label className="field-label" htmlFor="goalPeriod">
          Period
        </label>
        <select
          id="goalPeriod"
          value={draft.period}
          onChange={(event) => set('period', event.target.value as GoalPeriod)}
        >
          {GOAL_PERIODS.map((period) => (
            <option key={period} value={period}>
              {periodLabel(period)}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label className="field-label" htmlFor="goalTarget">
          Target ({metricUnit(draft.metric)})
        </label>
        <input
          id="goalTarget"
          type="number"
          step="any"
          value={draft.targetValue}
          onChange={(event) => set('targetValue', event.target.value)}
          aria-invalid={fieldError !== null}
          aria-describedby={fieldError !== null ? 'goalTarget-error' : undefined}
        />
        {fieldError !== null && (
          <span id="goalTarget-error" className="field-error" role="alert">
            {fieldError}
          </span>
        )}
      </div>

      {alreadyTaken && (
        <p className="empty-note" role="status">
          You already have a {periodLabel(draft.period).toLowerCase()}{' '}
          {metricLabel(draft.metric).toLowerCase()} goal. Change its target instead, or pick a
          different measure or period.
        </p>
      )}

      <button type="submit" className="primary-button" disabled={busy || alreadyTaken}>
        {busy ? 'Adding…' : 'Add goal'}
      </button>
    </form>
  );
}

interface GoalsCardProps {
  api: ApiClient;
}

/**
 * Creating, retargeting and retiring goals. Lives on the Profile page beside the other
 * settings cards; the dashboard's GoalsProgressCard is the read-only counterpart.
 */
export function GoalsCard({ api }: GoalsCardProps) {
  const [reloadKey, setReloadKey] = useState(0);
  const goals = useLoadable<GoalResponse[]>(() => listGoals(api), [api, reloadKey]);
  const reload = () => setReloadKey((current) => current + 1);

  const atCapacity = goals.state === 'ready' && goals.value.length >= MAX_ACTIVE_GOALS;

  return (
    <Card title="Goals">
      <p className="empty-note">
        Set your own targets. Unlike a streak, partial progress still counts — miss a day and you
        keep the rest of the period's progress.
      </p>

      {goals.state === 'loading' && <Skeleton height={160} />}
      {goals.state === 'error' && <ErrorNote message={goals.message} />}

      {goals.state === 'ready' && (
        <>
          {goals.value.length === 0 ? (
            <p className="empty-note">No goals yet.</p>
          ) : (
            <ul className="goal-manage-list">
              {goals.value.map((goal) => (
                <GoalListItem key={goal.id} api={api} goal={goal} onChanged={reload} />
              ))}
            </ul>
          )}

          {atCapacity ? (
            <p className="empty-note" role="status">
              You have the maximum of {MAX_ACTIVE_GOALS} goals. Remove one to add another.
            </p>
          ) : (
            <AddGoalForm api={api} existing={goals.value} onAdded={reload} />
          )}
        </>
      )}
    </Card>
  );
}
