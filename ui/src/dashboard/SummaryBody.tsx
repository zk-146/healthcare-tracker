import type { ReactNode } from 'react';
import type { SummaryResponse } from '../api/types';
import { useLoadable } from '../lib/useLoadable';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';
import { Stat } from '../ui/Stat';

/** "STRENGTH_TRAINING" -> "Strength training". Local rather than importing
 *  workouts/WorkoutForm's TYPE_LABELS, since bySource/byActivityType come back as
 *  free-form strings from the backend, not the ActivityType union. */
function titleCase(value: string): string {
  const lower = value.toLowerCase().replaceAll('_', ' ');
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

interface SummaryBodyProps {
  load: () => Promise<SummaryResponse>;
  /** Invalidation key: `load` is a fresh closure every render, so only a change here refetches. */
  loadKey: string;
  /** Rendered after the breakdowns, and only once the summary is ready. */
  children?: ReactNode;
}

/**
 * Split out of SummaryDetailsCard so the card can decline to mount it — useLoadable
 * fetches on mount, and an invalid custom range must not fire a request at all.
 */
export function SummaryBody({ load, loadKey, children }: SummaryBodyProps) {
  const summary = useLoadable<SummaryResponse>(load, [loadKey]);

  if (summary.state === 'loading') {
    return <Skeleton height={140} />;
  }
  if (summary.state === 'error') {
    return <ErrorNote message={summary.message} />;
  }

  const value = summary.value;
  return (
    <>
      <div className="stats-row">
        <Stat value={value.totalActivities.toLocaleString()} label="activities" />
        <Stat value={Math.round(value.totalDurationMinutes).toLocaleString()} label="minutes" />
        <Stat value={Math.round(value.totalCaloriesBurned).toLocaleString()} label="kcal" />
        <Stat value={value.totalDistanceKm.toFixed(1)} label="km" />
        <Stat value={Math.round(value.totalSteps).toLocaleString()} label="steps" />
        <Stat value={Math.round(value.averageDailyCalories).toLocaleString()} label="avg kcal/day" />
      </div>

      {value.byActivityType.length > 0 && (
        <ul className="breakdown-list">
          {value.byActivityType.map((row) => (
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

      {Object.keys(value.bySource).length > 0 && (
        <ul className="breakdown-list">
          {Object.entries(value.bySource).map(([source, count]) => (
            <li key={source}>
              <span>{titleCase(source)}</span>
              <span>{count}</span>
            </li>
          ))}
        </ul>
      )}

      {children}
    </>
  );
}
