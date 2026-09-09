import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from '../api/client';
import { ApiError } from '../api/client';
import { DashboardPage } from './DashboardPage';

const today = new Date(2026, 7, 27);

const summary = {
  from: '2026-08-27',
  to: '2026-08-27',
  totalActivities: 0,
  totalDurationMinutes: 0,
  totalCaloriesBurned: 0,
  totalDistanceKm: 0,
  totalSteps: 0,
  streakDays: 12,
  averageDailyCalories: 0,
  bySource: {},
  byActivityType: [],
};

const activitiesPage = {
  content: [
    {
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
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 100,
};

function apiReturning(handlers: Record<string, unknown>): ApiClient {
  return {
    get: vi.fn(async (path: string) => {
      const key = Object.keys(handlers).find((k) => path.startsWith(k));
      if (key === undefined) {
        throw new ApiError(404, null);
      }
      const value = handlers[key];
      if (value instanceof Error) {
        throw value;
      }
      return value;
    }),
    post: vi.fn(),
  } as unknown as ApiClient;
}

describe('DashboardPage', () => {
  it('renders every card once all three calls resolve', async () => {
    const api = apiReturning({
      '/api/v1/summary/daily': summary,
      '/api/v1/activities': activitiesPage,
      '/api/v1/integrations/google-health/status': {
        connected: true,
        status: 'CONNECTED',
        lastSyncedAt: '2026-08-22T04:00:00',
      },
    });

    render(<DashboardPage api={api} today={today} />);

    await waitFor(() => expect(screen.getByText('12')).toBeInTheDocument());
    expect(screen.getByRole('heading', { name: /22 Aug/ })).toBeInTheDocument();
    expect(screen.getAllByTestId('chart-bar')).toHaveLength(7);
    expect(screen.getByText(/5 days ago/i)).toBeInTheDocument();
  });

  it('keeps the rest of the page usable when the summary call fails', async () => {
    const api = apiReturning({
      '/api/v1/summary/daily': new ApiError(500, { error: 'Internal error' }),
      '/api/v1/activities': activitiesPage,
      '/api/v1/integrations/google-health/status': {
        connected: false,
        status: null,
        lastSyncedAt: null,
      },
    });

    render(<DashboardPage api={api} today={today} />);

    await waitFor(() => expect(screen.getAllByRole('alert').length).toBeGreaterThan(0));
    expect(screen.getAllByTestId('chart-bar')).toHaveLength(7);
  });

  it('shows a specific message and does not retry on 429', async () => {
    const api = apiReturning({
      '/api/v1/summary/daily': new ApiError(429, { error: 'Too many requests' }),
      '/api/v1/activities': activitiesPage,
      '/api/v1/integrations/google-health/status': {
        connected: false,
        status: null,
        lastSyncedAt: null,
      },
    });

    render(<DashboardPage api={api} today={today} />);

    await waitFor(() =>
      expect(screen.getByText(/too many requests/i)).toBeInTheDocument(),
    );
    expect(api.get).toHaveBeenCalledTimes(3);
  });

  it('shows the empty state when the account has no activities', async () => {
    const api = apiReturning({
      '/api/v1/summary/daily': { ...summary, streakDays: 0 },
      '/api/v1/activities': { ...activitiesPage, content: [], totalElements: 0 },
      '/api/v1/integrations/google-health/status': {
        connected: false,
        status: null,
        lastSyncedAt: null,
      },
    });

    render(<DashboardPage api={api} today={today} />);

    await waitFor(() =>
      expect(screen.getByText(/no activity data yet/i)).toBeInTheDocument(),
    );
  });

  it('reports real staleness instead of "no data" when the newest row is outside the 7-day chart window', async () => {
    const staleActivitiesPage = {
      ...activitiesPage,
      content: [{ ...activitiesPage.content[0], startedAt: '2026-08-22T00:00:00' }],
    };
    const farFutureToday = new Date(2026, 8, 5); // 14 days after the fixture's 22 Aug row

    const api = apiReturning({
      '/api/v1/summary/daily': summary,
      '/api/v1/activities': staleActivitiesPage,
      '/api/v1/integrations/google-health/status': {
        connected: true,
        status: 'CONNECTED',
        lastSyncedAt: '2026-08-22T04:00:00',
      },
    });

    render(<DashboardPage api={api} today={farFutureToday} />);

    await waitFor(() => expect(screen.getByText(/14 days ago/i)).toBeInTheDocument());
    expect(screen.queryByText(/no activity data yet/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/no data imported yet/i)).not.toBeInTheDocument();
  });

  it('surfaces an error instead of silently truncating when the window has more rows than one page', async () => {
    const truncatedPage = { ...activitiesPage, totalElements: 150 };
    const api = apiReturning({
      '/api/v1/summary/daily': summary,
      '/api/v1/activities': truncatedPage,
      '/api/v1/integrations/google-health/status': {
        connected: false,
        status: null,
        lastSyncedAt: null,
      },
    });

    render(<DashboardPage api={api} today={today} />);

    await waitFor(() => expect(screen.getByText(/truncated/i)).toBeInTheDocument());
  });
});
