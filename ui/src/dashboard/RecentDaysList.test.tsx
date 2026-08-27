import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { DayBucket } from '../lib/days';
import { RecentDaysList } from './RecentDaysList';

function bucket(dayKey: string, steps: number): DayBucket {
  return { dayKey, steps, distanceKm: 4.17, calories: 1452, durationMinutes: 247, activityCount: 1 };
}

describe('RecentDaysList', () => {
  it('lists populated days newest first', () => {
    render(
      <RecentDaysList
        buckets={[bucket('2026-08-20', 100), bucket('2026-08-21', 200), bucket('2026-08-22', 300)]}
      />,
    );
    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent(/22 Aug/);
    expect(items[2]).toHaveTextContent(/20 Aug/);
  });

  it('omits days with no activity rather than listing empty rows', () => {
    render(
      <RecentDaysList
        buckets={[
          bucket('2026-08-21', 200),
          { dayKey: '2026-08-22', steps: 0, distanceKm: 0, calories: 0, durationMinutes: 0, activityCount: 0 },
        ]}
      />,
    );
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
  });

  it('caps the list at the supplied limit', () => {
    const buckets = ['2026-08-18', '2026-08-19', '2026-08-20', '2026-08-21'].map((d) => bucket(d, 100));
    render(<RecentDaysList buckets={buckets} limit={2} />);
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });

  it('shows an empty state when nothing is populated', () => {
    render(<RecentDaysList buckets={[]} />);
    expect(screen.getByText(/no days recorded/i)).toBeInTheDocument();
  });
});
