import type { ActivityResponse } from '../api/types';

export interface DayBucket {
  /** 'YYYY-MM-DD'. */
  dayKey: string;
  steps: number;
  distanceKm: number;
  calories: number;
  durationMinutes: number;
  activityCount: number;
}

export interface WindowTotals {
  steps: number;
  distanceKm: number;
  calories: number;
}

const MS_PER_DAY = 86_400_000;

/** Formats a Date as a local-calendar day key. */
export function toDayKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Takes the date part of a zoneless LocalDateTime at face value.
 *
 * Correct for CSV_IMPORT rows, which are whole-day at 00:00:00. See the skipped
 * regression test in days.test.ts for why IOT rows will need conversion.
 */
export function dayKeyOf(startedAt: string): string {
  return startedAt.slice(0, 10);
}

/** Ascending day keys for the `days`-long window ending on `end` inclusive. */
export function rollingWindow(end: Date, days: number): string[] {
  const keys: string[] = [];
  for (let offset = days - 1; offset >= 0; offset--) {
    keys.push(toDayKey(new Date(end.getFullYear(), end.getMonth(), end.getDate() - offset)));
  }
  return keys;
}

/**
 * Buckets activities into exactly one entry per supplied day key, in the order given.
 * Days with no activities are present with zeroed metrics — the chart relies on this
 * so that gaps render as empty bars rather than disappearing.
 */
export function bucketByDay(
  activities: ActivityResponse[],
  dayKeys: string[],
): DayBucket[] {
  const byKey = new Map<string, DayBucket>();
  for (const dayKey of dayKeys) {
    byKey.set(dayKey, {
      dayKey,
      steps: 0,
      distanceKm: 0,
      calories: 0,
      durationMinutes: 0,
      activityCount: 0,
    });
  }

  for (const activity of activities) {
    const bucket = byKey.get(dayKeyOf(activity.startedAt));
    if (bucket === undefined) {
      continue;
    }
    bucket.steps += activity.steps ?? 0;
    bucket.distanceKm += activity.distanceKm ?? 0;
    bucket.calories += activity.caloriesBurned ?? 0;
    bucket.durationMinutes += activity.durationMinutes ?? 0;
    bucket.activityCount += 1;
  }

  return dayKeys.map((key) => byKey.get(key)!);
}

export function totals(buckets: DayBucket[]): WindowTotals {
  return buckets.reduce<WindowTotals>(
    (acc, bucket) => ({
      steps: acc.steps + bucket.steps,
      distanceKm: acc.distanceKm + bucket.distanceKm,
      calories: acc.calories + bucket.calories,
    }),
    { steps: 0, distanceKm: 0, calories: 0 },
  );
}

/** e.g. "Sat 22 Aug". */
export function formatDayLabel(dayKey: string, locale?: string): string {
  const [year, month, day] = dayKey.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  });
}

/** Whole calendar days between `dayKey` and `today`. Zero when they are the same day. */
export function daysSince(dayKey: string, today: Date): number {
  const [year, month, day] = dayKey.split('-').map(Number);
  const then = new Date(year, month - 1, day).getTime();
  const now = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  return Math.round((now - then) / MS_PER_DAY);
}
