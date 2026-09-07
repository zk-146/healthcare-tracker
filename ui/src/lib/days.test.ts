import { describe, expect, it } from 'vitest';
import type { ActivityResponse } from '../api/types';
import {
  bucketByDay,
  dayKeyOf,
  daysSince,
  formatDayLabel,
  rollingWindow,
  toDayKey,
  totals,
} from './days';

function makeActivity(overrides: Partial<ActivityResponse>): ActivityResponse {
  return {
    id: 'a1',
    activityType: 'WALKING',
    source: 'CSV_IMPORT',
    deviceId: null,
    startedAt: '2026-08-22T00:00:00',
    endedAt: '2026-08-22T23:59:59',
    durationMinutes: 247,
    distanceKm: 4.17,
    caloriesBurned: 1452,
    heartRateAvg: null,
    steps: 6307,
    notes: null,
    createdAt: '2026-08-23T08:52:43',
    updatedAt: '2026-08-23T08:52:43',
    ...overrides,
  };
}

describe('toDayKey / dayKeyOf', () => {
  it('formats a Date as a zero-padded local day key', () => {
    expect(toDayKey(new Date(2026, 7, 3))).toBe('2026-08-03');
  });

  it('takes the date part of a zoneless LocalDateTime', () => {
    expect(dayKeyOf('2026-08-22T00:00:00')).toBe('2026-08-22');
  });
});

describe('rollingWindow', () => {
  it('returns seven ascending day keys ending on the given day', () => {
    expect(rollingWindow(new Date(2026, 7, 27), 7)).toEqual([
      '2026-08-21',
      '2026-08-22',
      '2026-08-23',
      '2026-08-24',
      '2026-08-25',
      '2026-08-26',
      '2026-08-27',
    ]);
  });

  it('crosses a month boundary correctly', () => {
    expect(rollingWindow(new Date(2026, 8, 2), 3)).toEqual([
      '2026-08-31',
      '2026-09-01',
      '2026-09-02',
    ]);
  });
});

describe('bucketByDay', () => {
  const keys = rollingWindow(new Date(2026, 7, 27), 7);

  it('produces one bucket per day key even when no data exists', () => {
    const buckets = bucketByDay([], keys);
    expect(buckets).toHaveLength(7);
    expect(buckets.every((b) => b.steps === 0 && b.activityCount === 0)).toBe(true);
  });

  it('leaves gap days at zero while filling populated days', () => {
    const buckets = bucketByDay(
      [makeActivity({ startedAt: '2026-08-22T00:00:00', steps: 6307 })],
      keys,
    );
    expect(buckets.find((b) => b.dayKey === '2026-08-22')!.steps).toBe(6307);
    expect(buckets.find((b) => b.dayKey === '2026-08-25')!.steps).toBe(0);
    expect(buckets.find((b) => b.dayKey === '2026-08-27')!.activityCount).toBe(0);
  });

  it('sums multiple activities landing on the same day', () => {
    const buckets = bucketByDay(
      [
        makeActivity({ id: 'a', startedAt: '2026-08-23T07:00:00', steps: 100, distanceKm: 1 }),
        makeActivity({ id: 'b', startedAt: '2026-08-23T18:00:00', steps: 250, distanceKm: 2.5 }),
      ],
      keys,
    );
    const day = buckets.find((b) => b.dayKey === '2026-08-23')!;
    expect(day.steps).toBe(350);
    expect(day.distanceKm).toBeCloseTo(3.5);
    expect(day.activityCount).toBe(2);
  });

  it('treats null metrics as zero rather than NaN', () => {
    const buckets = bucketByDay(
      [makeActivity({ startedAt: '2026-08-24T00:00:00', steps: null, distanceKm: null })],
      keys,
    );
    const day = buckets.find((b) => b.dayKey === '2026-08-24')!;
    expect(day.steps).toBe(0);
    expect(day.distanceKm).toBe(0);
    expect(day.activityCount).toBe(1);
  });

  it('ignores activities outside the window', () => {
    const buckets = bucketByDay(
      [makeActivity({ startedAt: '2026-07-01T00:00:00', steps: 9999 })],
      keys,
    );
    expect(totals(buckets).steps).toBe(0);
  });

  // KNOWN FAILURE — un-skip when GoogleHealthClient.parseTime stops normalising to UTC.
  //
  // src/main/java/com/healthcare/activitytracker/service/GoogleHealthClient.java:130 stores
  // UTC wall-clock: OffsetDateTime.parse(v).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().
  // A 05:00 IST workout on 23 Aug therefore persists as 2026-08-22T23:30 and buckets onto 22 Aug.
  // Unreachable today because zero IOT-source rows exist; this test is the tripwire for the day
  // watch sync starts producing them.
  it.skip('buckets a UTC-stored early-morning workout onto the local day it happened', () => {
    const buckets = bucketByDay(
      [makeActivity({ source: 'IOT', startedAt: '2026-08-22T23:30:00', steps: 500 })],
      keys,
    );
    expect(buckets.find((b) => b.dayKey === '2026-08-23')!.steps).toBe(500);
  });
});

describe('totals', () => {
  it('sums buckets across the window', () => {
    const keys = rollingWindow(new Date(2026, 7, 27), 7);
    const buckets = bucketByDay(
      [
        makeActivity({ id: 'a', startedAt: '2026-08-22T00:00:00', steps: 100, distanceKm: 1, caloriesBurned: 1000 }),
        makeActivity({ id: 'b', startedAt: '2026-08-23T00:00:00', steps: 200, distanceKm: 2, caloriesBurned: 2000 }),
      ],
      keys,
    );
    expect(totals(buckets)).toEqual({ steps: 300, distanceKm: 3, calories: 3000 });
  });
});

describe('formatDayLabel', () => {
  it('renders a short weekday, day and month', () => {
    expect(formatDayLabel('2026-08-22', 'en-GB')).toBe('Sat 22 Aug');
  });
});

describe('daysSince', () => {
  it('counts whole days between a day key and today', () => {
    expect(daysSince('2026-08-22', new Date(2026, 7, 27))).toBe(5);
    expect(daysSince('2026-08-27', new Date(2026, 7, 27))).toBe(0);
  });
});
