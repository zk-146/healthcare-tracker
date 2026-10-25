import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import type { GoalProgress, GoalResponse } from '../api/types';
import { GoalsProgressCard } from './GoalsProgressCard';

function stubApi(get: ReturnType<typeof vi.fn>): ApiClient {
  return {
    get,
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
    postForm: vi.fn(),
  } as unknown as ApiClient;
}

/** Overrides allow a partial progress block so each test states only the fields it cares about. */
type GoalOverrides = Partial<Omit<GoalResponse, 'progress'>> & {
  progress?: Partial<GoalProgress>;
};

function goal(overrides: GoalOverrides = {}): GoalResponse {
  const base: GoalResponse = {
    id: 'g1',
    metric: 'MINUTES',
    period: 'WEEKLY',
    targetValue: 150,
    unit: 'minutes',
    createdAt: '2026-09-01T10:00:00',
    updatedAt: '2026-09-01T10:00:00',
    progress: {
      from: '2026-09-07',
      to: '2026-09-09',
      currentValue: 60,
      percentComplete: 40,
      achieved: false,
      daysElapsed: 3,
      daysTotal: 7,
      daysRemaining: 4,
      expectedByNow: 64.3,
      onTrack: false,
    },
  };
  return { ...base, ...overrides, progress: { ...base.progress, ...overrides.progress } };
}

function row(title: string): HTMLElement {
  return screen.getByText(title).closest('li') as HTMLElement;
}

describe('GoalsProgressCard', () => {
  it('renders each goal with its standing and pace note', async () => {
    render(<GoalsProgressCard api={stubApi(vi.fn().mockResolvedValue([goal()]))} />);

    await screen.findByText('Weekly active minutes');
    const weekly = row('Weekly active minutes');

    expect(within(weekly).getByText('60 / 150 minutes')).toBeInTheDocument();
    expect(
      within(weekly).getByText('4 minutes behind pace, 90 to go with 4 days left.'),
    ).toBeInTheDocument();
    expect(weekly).toHaveAttribute('data-on-track', 'false');
  });

  it('exposes the bar to assistive tech with the target as the maximum', async () => {
    render(<GoalsProgressCard api={stubApi(vi.fn().mockResolvedValue([goal()]))} />);

    const bar = await screen.findByRole('progressbar', {
      name: /progress toward weekly active minutes goal/i,
    });

    expect(bar).toHaveAttribute('aria-valuenow', '60');
    expect(bar).toHaveAttribute('aria-valuemax', '150');
  });

  it('marks an on-track goal and says what is left', async () => {
    const onTrack = goal({
      progress: { currentValue: 100, percentComplete: 66.7, onTrack: true },
    });
    render(<GoalsProgressCard api={stubApi(vi.fn().mockResolvedValue([onTrack]))} />);

    await screen.findByText('Weekly active minutes');
    const weekly = row('Weekly active minutes');

    expect(weekly).toHaveAttribute('data-on-track', 'true');
    expect(
      within(weekly).getByText('On track, 50 minutes to go with 4 days left.'),
    ).toBeInTheDocument();
  });

  it('renders several goals of different periods together', async () => {
    const goals = [
      goal(),
      goal({
        id: 'g2',
        metric: 'STEPS',
        period: 'DAILY',
        targetValue: 8_000,
        unit: 'steps',
        progress: {
          from: '2026-09-09',
          to: '2026-09-09',
          currentValue: 8_200,
          percentComplete: 102.5,
          achieved: true,
          daysElapsed: 1,
          daysTotal: 1,
          daysRemaining: 0,
          expectedByNow: 8_000,
          onTrack: true,
        },
      }),
    ];
    render(<GoalsProgressCard api={stubApi(vi.fn().mockResolvedValue(goals))} />);

    expect(await screen.findByText('Weekly active minutes')).toBeInTheDocument();

    const daily = row('Daily steps');
    expect(within(daily).getByText('8,200 / 8,000 steps')).toBeInTheDocument();
    expect(within(daily).getByText('Goal met — 8,200 of 8,000 steps.')).toBeInTheDocument();
  });

  it('renders nothing at all when no goals are set', async () => {
    const { container } = render(
      <GoalsProgressCard api={stubApi(vi.fn().mockResolvedValue([]))} />,
    );

    // An empty frame on the dashboard would be dead space; goals are created on Profile.
    await vi.waitFor(() => expect(container).toBeEmptyDOMElement());
    expect(screen.queryByText('Goals')).not.toBeInTheDocument();
  });

  it('shows an error note when the request fails', async () => {
    const get = vi.fn().mockRejectedValue(new ApiError(500, { error: 'Internal error' }));
    render(<GoalsProgressCard api={stubApi(get)} />);

    expect(await screen.findByText(/internal error/i)).toBeInTheDocument();
  });
});
