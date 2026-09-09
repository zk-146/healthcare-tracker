import { useEffect, useState } from 'react';
import { ApiError, type ApiClient } from '../api/client';
import { getDailySummary, getSyncStatus, listActivities } from '../api/endpoints';
import type { ActivityResponse, GoogleHealthStatusResponse, SummaryResponse } from '../api/types';
import { bucketByDay, dayKeyOf, rollingWindow, type DayBucket } from '../lib/days';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';
import { FreshnessCard } from './FreshnessCard';
import { LatestDayCard } from './LatestDayCard';
import { RecentDaysList } from './RecentDaysList';
import { SevenDayChart } from './SevenDayChart';
import { StreakHero } from './StreakHero';

const LOOKBACK_DAYS = 30; // fetch window: wide enough to find the newest row even when stale
const CHART_DAYS = 7;     // display window: what the chart and recent-days list show

interface DashboardPageProps {
  api: ApiClient;
  onSignOut(): void;
  /** Injectable for tests; defaults to now. */
  today?: Date;
}

type Loadable<T> = { state: 'loading' } | { state: 'ready'; value: T } | { state: 'error'; message: string };

function messageFor(cause: unknown): string {
  if (cause instanceof ApiError) {
    if (cause.status === 429) {
      return 'Too many requests — wait a minute and reload.';
    }
    return cause.body?.error ?? `Request failed (${cause.status})`;
  }
  if (cause instanceof Error) {
    return cause.message;
  }
  return 'Could not reach the server.';
}

function useLoadable<T>(load: () => Promise<T>, deps: unknown[]): Loadable<T> {
  const [result, setResult] = useState<Loadable<T>>({ state: 'loading' });

  useEffect(() => {
    let cancelled = false;
    setResult({ state: 'loading' });
    load()
      .then((value) => {
        if (!cancelled) {
          setResult({ state: 'ready', value });
        }
      })
      .catch((cause: unknown) => {
        if (!cancelled) {
          setResult({ state: 'error', message: messageFor(cause) });
        }
      });
    return () => {
      cancelled = true;
    };
    // `deps` is passed through deliberately: each caller controls its own invalidation.
  }, deps);

  return result;
}

export function DashboardPage({ api, onSignOut, today = new Date() }: DashboardPageProps) {
  const lookbackKeys = rollingWindow(today, LOOKBACK_DAYS);
  const chartKeys = lookbackKeys.slice(-CHART_DAYS);
  const from = lookbackKeys[0];
  const to = lookbackKeys[lookbackKeys.length - 1];

  const summary = useLoadable<SummaryResponse>(() => getDailySummary(api), [api]);
  const activities = useLoadable<ActivityResponse[]>(
    () =>
      listActivities(api, from, to).then((page) => {
        if (page.totalElements > page.content.length) {
          throw new Error(
            `Activity data was truncated: ${page.totalElements} rows exist for this window but only ${page.content.length} were loaded.`,
          );
        }
        return page.content;
      }),
    [api, from, to],
  );
  const sync = useLoadable<GoogleHealthStatusResponse>(() => getSyncStatus(api), [api]);

  const lookbackBuckets: DayBucket[] =
    activities.state === 'ready' ? bucketByDay(activities.value, lookbackKeys) : [];
  const chartBuckets: DayBucket[] =
    activities.state === 'ready' ? bucketByDay(activities.value, chartKeys) : [];

  const latestDayKey =
    activities.state === 'ready' && activities.value.length > 0
      ? activities.value
          .map((activity) => dayKeyOf(activity.startedAt))
          .sort((a, b) => a.localeCompare(b))
          .at(-1) ?? null
      : null;

  const latestBucket = lookbackBuckets.find((bucket) => bucket.dayKey === latestDayKey) ?? null;

  return (
    <main className="page">
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <h1 style={{ fontSize: 18, margin: 0 }}>Activity</h1>
        <button type="button" onClick={onSignOut} style={{ background: 'none', border: 0, color: 'inherit', opacity: 0.6 }}>
          Sign out
        </button>
      </header>

      {summary.state === 'loading' && <Skeleton height={150} />}
      {summary.state === 'error' && <ErrorNote message={summary.message} />}
      {summary.state === 'ready' && <StreakHero streakDays={summary.value.streakDays} />}

      {activities.state === 'loading' && <Skeleton height={110} />}
      {activities.state === 'error' && <ErrorNote message={activities.message} />}
      {activities.state === 'ready' && (
        <>
          <LatestDayCard bucket={latestBucket} />
          <SevenDayChart buckets={chartBuckets} />
          <RecentDaysList buckets={chartBuckets} />
        </>
      )}

      {sync.state !== 'loading' && (
        <FreshnessCard
          latestDayKey={latestDayKey}
          today={today}
          status={sync.state === 'ready' ? sync.value : null}
          syncError={sync.state === 'error'}
        />
      )}
    </main>
  );
}
