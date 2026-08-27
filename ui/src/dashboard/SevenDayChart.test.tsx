import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { bucketByDay, rollingWindow } from '../lib/days';
import type { ActivityResponse } from '../api/types';
import { SevenDayChart } from './SevenDayChart';

function activity(startedAt: string, steps: number): ActivityResponse {
  return {
    id: startedAt,
    activityType: 'WALKING',
    source: 'CSV_IMPORT',
    deviceId: null,
    startedAt,
    endedAt: null,
    durationMinutes: 60,
    distanceKm: 1,
    caloriesBurned: 1000,
    heartRateAvg: null,
    steps,
    notes: null,
    createdAt: startedAt,
    updatedAt: startedAt,
  };
}

const keys = rollingWindow(new Date(2026, 7, 27), 7);

describe('SevenDayChart', () => {
  it('always renders seven bars, including for days with no data', () => {
    const buckets = bucketByDay([activity('2026-08-22T00:00:00', 6307)], keys);
    render(<SevenDayChart buckets={buckets} />);
    expect(screen.getAllByTestId('chart-bar')).toHaveLength(7);
  });

  it('marks days without data as empty so gaps are visible', () => {
    const buckets = bucketByDay([activity('2026-08-22T00:00:00', 6307)], keys);
    render(<SevenDayChart buckets={buckets} />);
    const bars = screen.getAllByTestId('chart-bar');
    const empties = bars.filter((bar) => bar.getAttribute('data-empty') === 'true');
    expect(empties).toHaveLength(6);
  });

  it('shows window totals that equal the sum of the bars', () => {
    const buckets = bucketByDay(
      [activity('2026-08-22T00:00:00', 100), activity('2026-08-23T00:00:00', 250)],
      keys,
    );
    render(<SevenDayChart buckets={buckets} />);
    expect(screen.getByText(/350 steps/)).toBeInTheDocument();
  });

  it('renders without crashing when every day is empty', () => {
    render(<SevenDayChart buckets={bucketByDay([], keys)} />);
    expect(screen.getAllByTestId('chart-bar')).toHaveLength(7);
    expect(screen.getByText(/0 steps/)).toBeInTheDocument();
  });
});
