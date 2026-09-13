import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import type { SummaryResponse } from '../api/types';
import { SummaryBody } from './SummaryBody';

function summaryFor(overrides: Partial<SummaryResponse> = {}): SummaryResponse {
  return {
    from: '2026-08-21',
    to: '2026-08-27',
    totalActivities: 4,
    totalDurationMinutes: 210,
    totalCaloriesBurned: 1800,
    totalDistanceKm: 12.5,
    totalSteps: 15000,
    streakDays: 3,
    averageDailyCalories: 257,
    bySource: { MANUAL: 3, CSV_IMPORT: 1 },
    byActivityType: [
      { type: 'STRENGTH_TRAINING', source: 'MANUAL', count: 2, totalMinutes: 90, totalCalories: 900 },
    ],
    ...overrides,
  };
}

describe('SummaryBody', () => {
  it('shows a skeleton and hides children while loading', () => {
    const load = vi.fn().mockReturnValue(new Promise(() => {}));
    const { container } = render(
      <SummaryBody load={load} loadKey="weekly">
        <p>child</p>
      </SummaryBody>,
    );

    expect(container.querySelector('.skeleton')).toBeInTheDocument();
    expect(screen.queryByText('child')).not.toBeInTheDocument();
  });

  it('renders totals, breakdowns and children once loaded', async () => {
    const load = vi.fn().mockResolvedValue(summaryFor());
    render(
      <SummaryBody load={load} loadKey="weekly">
        <p>child</p>
      </SummaryBody>,
    );

    expect(await screen.findByText('4')).toBeInTheDocument();
    expect(screen.getByText('Strength training')).toBeInTheDocument();
    expect(screen.getByText(/2 · 90 min · 900 kcal/)).toBeInTheDocument();
    expect(screen.getByText('Csv import')).toBeInTheDocument();
    expect(screen.getByText('child')).toBeInTheDocument();
  });

  it('shows an error note and no children when loading fails', async () => {
    const load = vi.fn().mockRejectedValue(new ApiError(500, { error: 'Internal error' }));
    render(
      <SummaryBody load={load} loadKey="weekly">
        <p>child</p>
      </SummaryBody>,
    );

    expect(await screen.findByText(/internal error/i)).toBeInTheDocument();
    expect(screen.queryByText('child')).not.toBeInTheDocument();
  });

  it('reloads when loadKey changes, and not otherwise', async () => {
    const load = vi.fn().mockResolvedValue(summaryFor());
    const { rerender } = render(<SummaryBody load={load} loadKey="weekly" />);
    await screen.findByText('4');

    rerender(<SummaryBody load={load} loadKey="weekly" />);
    expect(load).toHaveBeenCalledTimes(1);

    rerender(<SummaryBody load={load} loadKey="monthly" />);
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
  });
});
