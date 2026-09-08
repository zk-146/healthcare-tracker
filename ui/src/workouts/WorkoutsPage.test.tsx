import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from '../api/client';
import type { ActivityResponse } from '../api/types';
import { WorkoutsPage } from './WorkoutsPage';

function activity(overrides: Partial<ActivityResponse> = {}): ActivityResponse {
  return {
    id: 'a1',
    activityType: 'CYCLING',
    source: 'MANUAL',
    deviceId: null,
    startedAt: '2026-09-07T18:15:00',
    endedAt: null,
    durationMinutes: 50,
    distanceKm: 18.4,
    caloriesBurned: null,
    heartRateAvg: null,
    steps: null,
    notes: null,
    createdAt: '2026-09-07T19:10:00',
    updatedAt: '2026-09-07T19:10:00',
    ...overrides,
  };
}

function pageOf(content: ActivityResponse[], number = 0, totalPages = 1) {
  return { content, totalElements: content.length, totalPages, number, size: 20 };
}

function apiWithPages(pages: Record<number, ReturnType<typeof pageOf>>): ApiClient {
  return {
    get: vi.fn(async (path: string) => {
      const match = /page=(\d+)/.exec(path);
      return pages[Number(match?.[1] ?? 0)];
    }),
    post: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
    del: vi.fn().mockResolvedValue(undefined),
  } as unknown as ApiClient;
}

describe('WorkoutsPage', () => {
  it('renders a row per activity once the first page resolves', async () => {
    const api = apiWithPages({ 0: pageOf([activity()]) });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} />);

    expect(await screen.findByText('Cycling')).toBeInTheDocument();
    expect(screen.getByText('50 min')).toBeInTheDocument();
    expect(screen.getByText('18.4 km')).toBeInTheDocument();
  });

  it('shows an empty note when there is no history', async () => {
    const api = apiWithPages({ 0: pageOf([]) });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} />);

    expect(await screen.findByText('No workouts logged yet.')).toBeInTheDocument();
  });

  it('appends the next page and hides Load more at the last page', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({
      0: pageOf([activity({ id: 'a1', activityType: 'CYCLING' })], 0, 2),
      1: pageOf([activity({ id: 'a2', activityType: 'RUNNING' })], 1, 2),
    });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} />);

    await user.click(await screen.findByRole('button', { name: 'Load more' }));

    expect(await screen.findByText('Running')).toBeInTheDocument();
    expect(screen.getByText('Cycling')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument(),
    );
  });

  it('badges imported and device rows but not manual ones', async () => {
    const api = apiWithPages({
      0: pageOf([
        activity({ id: 'a1', source: 'MANUAL' }),
        activity({ id: 'a2', source: 'CSV_IMPORT', activityType: 'WALKING' }),
        activity({ id: 'a3', source: 'IOT', activityType: 'RUNNING' }),
      ]),
    });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} />);

    expect(await screen.findByText('imported')).toBeInTheDocument();
    expect(screen.getByText('device')).toBeInTheDocument();
    expect(screen.queryByText('manual')).not.toBeInTheDocument();
  });

  it('opens the edit dialog when a row is tapped', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([activity()]) });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} />);

    await user.click(await screen.findByRole('button', { name: /Cycling/ }));

    expect(await screen.findByRole('dialog', { name: 'Edit workout' })).toBeInTheDocument();
  });

  it('refetches page 0 after a successful edit', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([activity()]) });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} />);

    await user.click(await screen.findByRole('button', { name: /Cycling/ }));
    await user.click(await screen.findByRole('button', { name: 'Save workout' }));

    await waitFor(() => expect(api.put).toHaveBeenCalled());
    await waitFor(() => {
      const pageZeroCalls = (api.get as unknown as { mock: { calls: string[][] } }).mock.calls.filter(
        ([path]) => path.includes('page=0'),
      );
      expect(pageZeroCalls.length).toBe(2);
    });
  });

  it('opens the create dialog when the parent says so, and reports it closed', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([]) });
    const onCreateClose = vi.fn();
    render(<WorkoutsPage api={api} createOpen onCreateClose={onCreateClose} />);

    expect(await screen.findByRole('dialog', { name: 'Log workout' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Close' }));

    expect(onCreateClose).toHaveBeenCalled();
  });

  it('surfaces a load failure', async () => {
    const api = {
      get: vi.fn().mockRejectedValue(new Error('offline')),
      post: vi.fn(),
      put: vi.fn(),
      del: vi.fn(),
    } as unknown as ApiClient;
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} />);

    expect(await screen.findByText('offline')).toBeInTheDocument();
  });
});
