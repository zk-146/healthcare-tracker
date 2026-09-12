import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import type { DigestResponse, SummaryResponse } from '../api/types';
import { SummaryDetailsCard } from './SummaryDetailsCard';

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
      { type: 'RUNNING', source: 'MANUAL', count: 2, totalMinutes: 90, totalCalories: 900 },
      { type: 'YOGA', source: 'MANUAL', count: 1, totalMinutes: 30, totalCalories: 100 },
    ],
    ...overrides,
  };
}

function stubApi(get: ReturnType<typeof vi.fn>): ApiClient {
  return { get, post: vi.fn(), put: vi.fn(), del: vi.fn() } as unknown as ApiClient;
}

describe('SummaryDetailsCard', () => {
  it('loads the weekly summary by default and shows totals and breakdowns', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} />);

    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/v1/summary/weekly'));
    expect(await screen.findByText('4')).toBeInTheDocument();
    expect(screen.getByText('Running')).toBeInTheDocument();
    expect(screen.getByText(/2 · 90 min · 900 kcal/)).toBeInTheDocument();
    expect(screen.getByText('Manual')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('refetches from the matching endpoint when the period changes', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} />);
    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/v1/summary/weekly'));

    await userEvent.click(screen.getByRole('button', { name: 'Day' }));
    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/v1/summary/daily'));

    await userEvent.click(screen.getByRole('button', { name: 'Month' }));
    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/v1/summary/monthly'));
  });

  it('marks the active period button as pressed', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} />);
    await screen.findByText('4');

    expect(screen.getByRole('button', { name: 'Week' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Day' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('shows an error note when the summary call fails', async () => {
    const get = vi.fn().mockRejectedValue(new ApiError(500, { error: 'Internal error' }));
    render(<SummaryDetailsCard api={stubApi(get)} />);

    expect(await screen.findByText(/internal error/i)).toBeInTheDocument();
  });

  it('loads and displays the AI recap on demand', async () => {
    const digest: DigestResponse = {
      period: 'weekly',
      from: '2026-08-21',
      to: '2026-08-27',
      available: true,
      digest: 'Great week — 4 workouts and a 3-day streak.',
    };
    const get = vi
      .fn()
      .mockImplementation((path: string) =>
        path.startsWith('/api/v1/summary/digest')
          ? Promise.resolve(digest)
          : Promise.resolve(summaryFor()),
      );
    render(<SummaryDetailsCard api={stubApi(get)} />);
    await screen.findByText('4');

    await userEvent.click(screen.getByRole('button', { name: /generate ai recap/i }));

    expect(get).toHaveBeenCalledWith('/api/v1/summary/digest?period=weekly');
    expect(await screen.findByText(/great week/i)).toBeInTheDocument();
  });

  it('shows the fallback note when the digest is unavailable', async () => {
    const digest: DigestResponse = {
      period: 'weekly',
      from: '2026-08-21',
      to: '2026-08-27',
      available: false,
      digest: 'AI recaps are temporarily offline.',
    };
    const get = vi
      .fn()
      .mockImplementation((path: string) =>
        path.startsWith('/api/v1/summary/digest')
          ? Promise.resolve(digest)
          : Promise.resolve(summaryFor()),
      );
    render(<SummaryDetailsCard api={stubApi(get)} />);
    await screen.findByText('4');

    await userEvent.click(screen.getByRole('button', { name: /generate ai recap/i }));

    expect(await screen.findByText(/temporarily offline/i)).toBeInTheDocument();
    expect(await screen.findByText(/unavailable right now/i)).toBeInTheDocument();
  });

  it('clears a loaded digest when the period changes', async () => {
    const digest: DigestResponse = {
      period: 'weekly',
      from: '2026-08-21',
      to: '2026-08-27',
      available: true,
      digest: 'Great week — 4 workouts and a 3-day streak.',
    };
    const get = vi
      .fn()
      .mockImplementation((path: string) =>
        path.startsWith('/api/v1/summary/digest')
          ? Promise.resolve(digest)
          : Promise.resolve(summaryFor()),
      );
    render(<SummaryDetailsCard api={stubApi(get)} />);
    await screen.findByText('4');
    await userEvent.click(screen.getByRole('button', { name: /generate ai recap/i }));
    await screen.findByText(/great week/i);

    await userEvent.click(screen.getByRole('button', { name: 'Day' }));

    await waitFor(() => {
      expect(screen.queryByText(/great week/i)).not.toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /generate ai recap/i })).toBeInTheDocument();
  });
});
