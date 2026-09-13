import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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

const now = new Date(2026, 8, 13, 10, 0);
const DEFAULT_RANGE_PATH = '/api/v1/summary?from=2026-08-15&to=2026-09-13';

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

  it('loads the last 30 days when Custom is selected', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await screen.findByText('4');

    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));

    await waitFor(() => expect(get).toHaveBeenCalledWith(DEFAULT_RANGE_PATH));
    expect(screen.getByRole('button', { name: 'Custom' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByLabelText('From')).toHaveValue('2026-08-15');
    expect(screen.getByLabelText('To')).toHaveValue('2026-09-13');
  });

  it('does not show the date inputs for the fixed periods', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await screen.findByText('4');

    expect(screen.queryByLabelText('From')).not.toBeInTheDocument();
  });

  it('refetches when a custom date changes', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));
    await waitFor(() => expect(get).toHaveBeenCalledWith(DEFAULT_RANGE_PATH));

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-09-01' } });

    await waitFor(() =>
      expect(get).toHaveBeenCalledWith('/api/v1/summary?from=2026-09-01&to=2026-09-13'),
    );
  });

  it('shows the validation message and makes no request for an invalid range', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));
    await waitFor(() => expect(get).toHaveBeenCalledWith(DEFAULT_RANGE_PATH));
    get.mockClear();

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-09-20' } });

    expect(await screen.findByText('Start date must be on or before end date.')).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });

  it('rejects a future end date without making a request', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));
    await waitFor(() => expect(get).toHaveBeenCalledWith(DEFAULT_RANGE_PATH));
    get.mockClear();

    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-09-14' } });

    expect(await screen.findByText("End date can't be in the future.")).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });

  it('caps both custom date pickers at today', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));

    expect(screen.getByLabelText('From')).toHaveAttribute('max', '2026-09-13');
    expect(screen.getByLabelText('To')).toHaveAttribute('max', '2026-09-13');
  });

  it('moves the picker cap forward when the day changes while the card stays open', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    const { rerender } = render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));

    rerender(<SummaryDetailsCard api={stubApi(get)} now={new Date(2026, 8, 14, 0, 5)} />);

    expect(screen.getByLabelText('To')).toHaveAttribute('max', '2026-09-14');
  });

  it('keeps the custom dates when switching away and back', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-09-01' } });

    await userEvent.click(screen.getByRole('button', { name: 'Week' }));
    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));

    expect(screen.getByLabelText('From')).toHaveValue('2026-09-01');
  });

  it('does not offer the AI recap for a custom range', async () => {
    const get = vi.fn().mockResolvedValue(summaryFor());
    render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await screen.findByRole('button', { name: /generate ai recap/i });

    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));
    await waitFor(() => expect(get).toHaveBeenCalledWith(DEFAULT_RANGE_PATH));
    await screen.findByText('4');

    expect(screen.queryByRole('button', { name: /generate ai recap/i })).not.toBeInTheDocument();
  });

  it('clears a loaded digest when switching to Custom', async () => {
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
    render(<SummaryDetailsCard api={stubApi(get)} now={now} />);
    await screen.findByText('4');
    await userEvent.click(screen.getByRole('button', { name: /generate ai recap/i }));
    await screen.findByText(/great week/i);

    await userEvent.click(screen.getByRole('button', { name: 'Custom' }));
    await userEvent.click(screen.getByRole('button', { name: 'Week' }));

    await screen.findByRole('button', { name: /generate ai recap/i });
    expect(screen.queryByText(/great week/i)).not.toBeInTheDocument();
  });
});
