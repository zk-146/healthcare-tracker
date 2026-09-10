import { useEffect, useState } from 'react';
import { type ApiClient } from '../api/client';
import { getDigest, getSummaryFor } from '../api/endpoints';
import type { DigestResponse, SummaryPeriod, SummaryResponse } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import { useLoadable, type Loadable } from '../lib/useLoadable';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';
import { Stat } from '../ui/Stat';

const PERIOD_LABELS: Record<SummaryPeriod, string> = {
  daily: 'Day',
  weekly: 'Week',
  monthly: 'Month',
};

const PERIODS: SummaryPeriod[] = ['daily', 'weekly', 'monthly'];

/** "STRENGTH_TRAINING" -> "Strength training". Local rather than importing
 *  workouts/WorkoutForm's TYPE_LABELS, since bySource/byActivityType come back as
 *  free-form strings from the backend, not the ActivityType union. */
function titleCase(value: string): string {
  const lower = value.toLowerCase().replaceAll('_', ' ');
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

interface SummaryDetailsCardProps {
  api: ApiClient;
}

export function SummaryDetailsCard({ api }: SummaryDetailsCardProps) {
  const [period, setPeriod] = useState<SummaryPeriod>('weekly');
  const summary = useLoadable<SummaryResponse>(() => getSummaryFor(api, period), [api, period]);
  const [digest, setDigest] = useState<Loadable<DigestResponse> | null>(null);

  // A digest fetched for one period is stale (and possibly costly to regenerate) once the
  // period changes, so drop it rather than showing last period's recap under a new label.
  useEffect(() => {
    setDigest(null);
  }, [period]);

  async function loadDigest(): Promise<void> {
    setDigest({ state: 'loading' });
    try {
      const value = await getDigest(api, period);
      setDigest({ state: 'ready', value });
    } catch (cause: unknown) {
      setDigest({ state: 'error', message: messageFor(cause) });
    }
  }

  return (
    <Card title="Summary">
      <div className="segmented" role="group" aria-label="Summary period">
        {PERIODS.map((candidate) => (
          <button
            key={candidate}
            type="button"
            className="segmented-button"
            aria-pressed={period === candidate}
            onClick={() => setPeriod(candidate)}
          >
            {PERIOD_LABELS[candidate]}
          </button>
        ))}
      </div>

      {summary.state === 'loading' && <Skeleton height={140} />}
      {summary.state === 'error' && <ErrorNote message={summary.message} />}

      {summary.state === 'ready' && (
        <>
          <div className="stats-row">
            <Stat value={summary.value.totalActivities.toLocaleString()} label="activities" />
            <Stat value={Math.round(summary.value.totalDurationMinutes).toLocaleString()} label="minutes" />
            <Stat value={Math.round(summary.value.totalCaloriesBurned).toLocaleString()} label="kcal" />
            <Stat value={summary.value.totalDistanceKm.toFixed(1)} label="km" />
            <Stat value={Math.round(summary.value.totalSteps).toLocaleString()} label="steps" />
            <Stat
              value={Math.round(summary.value.averageDailyCalories).toLocaleString()}
              label="avg kcal/day"
            />
          </div>

          {summary.value.byActivityType.length > 0 && (
            <ul className="breakdown-list">
              {summary.value.byActivityType.map((row) => (
                <li key={`${row.type}-${row.source}`}>
                  <span>{titleCase(row.type)}</span>
                  <span>
                    {row.count} · {Math.round(row.totalMinutes)} min ·{' '}
                    {Math.round(row.totalCalories).toLocaleString()} kcal
                  </span>
                </li>
              ))}
            </ul>
          )}

          {Object.keys(summary.value.bySource).length > 0 && (
            <ul className="breakdown-list">
              {Object.entries(summary.value.bySource).map(([source, count]) => (
                <li key={source}>
                  <span>{titleCase(source)}</span>
                  <span>{count}</span>
                </li>
              ))}
            </ul>
          )}

          {digest === null && (
            <button type="button" className="link-button" onClick={() => void loadDigest()}>
              Generate AI recap
            </button>
          )}
          {digest?.state === 'loading' && <Skeleton height={60} />}
          {digest?.state === 'error' && <ErrorNote message={digest.message} />}
          {digest?.state === 'ready' && (
            <p className="digest-text">
              {digest.value.digest}
              {!digest.value.available && ' (AI recap unavailable right now.)'}
            </p>
          )}
        </>
      )}
    </Card>
  );
}
