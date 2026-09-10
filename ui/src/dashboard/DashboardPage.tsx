import { useState } from 'react';
import { type ApiClient } from '../api/client';
import { getDailySummary, getSyncStatus, listActivities } from '../api/endpoints';
import type { ActivityResponse, GoogleHealthStatusResponse, SummaryResponse } from '../api/types';
import { bucketByDay, dayKeyOf, rollingWindow, type DayBucket } from '../lib/days';
import { useLoadable } from '../lib/useLoadable';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';
import { FreshnessCard } from './FreshnessCard';
import { LatestDayCard } from './LatestDayCard';
import { RecentDaysList } from './RecentDaysList';
import { SevenDayChart } from './SevenDayChart';
import { StreakHero } from './StreakHero';
import { SummaryDetailsCard } from './SummaryDetailsCard';

const LOOKBACK_DAYS = 30; // fetch window: wide enough to find the newest row even when stale
const CHART_DAYS = 7;     // display window: what the chart and recent-days list show

interface DashboardPageProps {
  api: ApiClient;
  /** Injectable for tests; defaults to now. */
  today?: Date;
}

export function DashboardPage({ api, today = new Date() }: DashboardPageProps) {
  const [syncReloadKey, setSyncReloadKey] = useState(0);
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
  const sync = useLoadable<GoogleHealthStatusResponse>(
    () => getSyncStatus(api),
    [api, syncReloadKey],
  );

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
    <>
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

      <SummaryDetailsCard api={api} />

      {sync.state !== 'loading' && (
        <FreshnessCard
          api={api}
          latestDayKey={latestDayKey}
          today={today}
          status={sync.state === 'ready' ? sync.value : null}
          syncError={sync.state === 'error'}
          onDisconnected={() => setSyncReloadKey((current) => current + 1)}
        />
      )}
    </>
  );
}
