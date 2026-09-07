import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { DayBucket } from '../lib/days';
import { LatestDayCard } from './LatestDayCard';

const bucket: DayBucket = {
  dayKey: '2026-08-22',
  steps: 6307,
  distanceKm: 4.17,
  calories: 1452,
  durationMinutes: 247,
  activityCount: 1,
};

describe('LatestDayCard', () => {
  it('titles the card with the day the data is actually from', () => {
    render(<LatestDayCard bucket={bucket} />);
    expect(screen.getByRole('heading')).toHaveTextContent(/22 Aug/);
  });

  it('renders steps, distance and total burn', () => {
    render(<LatestDayCard bucket={bucket} />);
    expect(screen.getByText('6,307')).toBeInTheDocument();
    expect(screen.getByText('4.2')).toBeInTheDocument();
    expect(screen.getByText('1,452')).toBeInTheDocument();
  });

  it('labels calories as total burn rather than exercise calories', () => {
    render(<LatestDayCard bucket={bucket} />);
    expect(screen.getByText(/total burn/i)).toBeInTheDocument();
  });

  it('shows an explanatory empty state when there is no data at all', () => {
    render(<LatestDayCard bucket={null} />);
    expect(screen.getByText(/no activity data yet/i)).toBeInTheDocument();
  });
});
