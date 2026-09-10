import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import { FreshnessCard } from './FreshnessCard';

const today = new Date(2026, 7, 27);

function stubApi(overrides: Partial<ApiClient> = {}): ApiClient {
  return {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn().mockResolvedValue(undefined),
    postForm: vi.fn(),
    ...overrides,
  };
}

describe('FreshnessCard', () => {
  it('reports up-to-date when the newest row is today', () => {
    render(
      <FreshnessCard api={stubApi()} latestDayKey="2026-08-27" today={today} status={null} onDisconnected={vi.fn()} />,
    );
    expect(screen.getByText(/up to date/i)).toBeInTheDocument();
  });

  it('reports the age of stale data in days', () => {
    render(
      <FreshnessCard api={stubApi()} latestDayKey="2026-08-22" today={today} status={null} onDisconnected={vi.fn()} />,
    );
    expect(screen.getByText(/5 days ago/i)).toBeInTheDocument();
  });

  it('flags itself as stale so it can be styled differently', () => {
    const { container } = render(
      <FreshnessCard api={stubApi()} latestDayKey="2026-08-22" today={today} status={null} onDisconnected={vi.fn()} />,
    );
    expect(container.querySelector('[data-stale="true"]')).not.toBeNull();
  });

  it('is not stale at one day old', () => {
    const { container } = render(
      <FreshnessCard api={stubApi()} latestDayKey="2026-08-26" today={today} status={null} onDisconnected={vi.fn()} />,
    );
    expect(container.querySelector('[data-stale="true"]')).toBeNull();
  });

  it('is not stale at exactly the staleness boundary (two days old)', () => {
    const { container } = render(
      <FreshnessCard api={stubApi()} latestDayKey="2026-08-25" today={today} status={null} onDisconnected={vi.fn()} />,
    );
    expect(container.querySelector('[data-stale="true"]')).toBeNull();
  });

  it('prompts to reconnect when the integration needs reauthorisation', () => {
    render(
      <FreshnessCard
        api={stubApi()}
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: true, status: 'NEEDS_RECONNECT', lastSyncedAt: '2026-08-20T04:00:00' }}
        onDisconnected={vi.fn()}
      />,
    );
    expect(screen.getByText('Reconnect required')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reconnect/i })).toBeInTheDocument();
  });

  it('flags itself as stale when reconnect is required even if the data is fresh', () => {
    const { container } = render(
      <FreshnessCard
        api={stubApi()}
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: true, status: 'NEEDS_RECONNECT', lastSyncedAt: '2026-08-20T04:00:00' }}
        onDisconnected={vi.fn()}
      />,
    );
    expect(container.querySelector('[data-stale="true"]')).not.toBeNull();
  });

  it('says the watch is not connected when it is not', () => {
    render(
      <FreshnessCard
        api={stubApi()}
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: false, status: null, lastSyncedAt: null }}
        onDisconnected={vi.fn()}
      />,
    );
    expect(screen.getByText(/not connected/i)).toBeInTheDocument();
  });

  it('handles having no data at all', () => {
    render(
      <FreshnessCard api={stubApi()} latestDayKey={null} today={today} status={null} onDisconnected={vi.fn()} />,
    );
    expect(screen.getByText(/no data/i)).toBeInTheDocument();
  });

  it('flags sync status as unavailable when the sync call itself failed', () => {
    render(
      <FreshnessCard
        api={stubApi()}
        latestDayKey="2026-08-27"
        today={today}
        status={null}
        syncError
        onDisconnected={vi.fn()}
      />,
    );
    expect(screen.getByText(/sync status unavailable/i)).toBeInTheDocument();
  });

  it('offers to connect when the watch is not linked, opening the authorization URL', async () => {
    const get = vi.fn().mockResolvedValue({ authorizationUrl: 'https://accounts.google.com/auth' });
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    render(
      <FreshnessCard
        api={stubApi({ get })}
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: false, status: null, lastSyncedAt: null }}
        onDisconnected={vi.fn()}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: /connect watch/i }));

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith('/api/v1/integrations/google-health/connect');
    });
    expect(openSpy).toHaveBeenCalledWith(
      'https://accounts.google.com/auth',
      '_blank',
      'noopener,noreferrer',
    );
    expect(await screen.findByText(/opened in a new tab/i)).toBeInTheDocument();
    openSpy.mockRestore();
  });

  it('shows an error banner when starting the connect flow fails', async () => {
    const get = vi.fn().mockRejectedValue(new ApiError(503, { error: 'Service Unavailable' }));
    render(
      <FreshnessCard
        api={stubApi({ get })}
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: false, status: null, lastSyncedAt: null }}
        onDisconnected={vi.fn()}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: /connect watch/i }));

    expect(await screen.findByText(/service unavailable/i)).toBeInTheDocument();
  });

  it('disconnects a linked watch and notifies the parent', async () => {
    const del = vi.fn().mockResolvedValue(undefined);
    const onDisconnected = vi.fn();
    render(
      <FreshnessCard
        api={stubApi({ del })}
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: true, status: 'CONNECTED', lastSyncedAt: '2026-08-27T04:00:00' }}
        onDisconnected={onDisconnected}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: /disconnect/i }));

    await waitFor(() => {
      expect(del).toHaveBeenCalledWith('/api/v1/integrations/google-health');
    });
    expect(onDisconnected).toHaveBeenCalled();
  });

  it('shows an error banner when disconnecting fails, without notifying the parent', async () => {
    const del = vi.fn().mockRejectedValue(new Error('Could not reach the server.'));
    const onDisconnected = vi.fn();
    render(
      <FreshnessCard
        api={stubApi({ del })}
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: true, status: 'CONNECTED', lastSyncedAt: '2026-08-27T04:00:00' }}
        onDisconnected={onDisconnected}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: /disconnect/i }));

    expect(await screen.findByText(/could not reach the server/i)).toBeInTheDocument();
    expect(onDisconnected).not.toHaveBeenCalled();
  });
});
