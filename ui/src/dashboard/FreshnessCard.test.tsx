import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { FreshnessCard } from './FreshnessCard';

const today = new Date(2026, 7, 27);

describe('FreshnessCard', () => {
  it('reports up-to-date when the newest row is today', () => {
    render(<FreshnessCard latestDayKey="2026-08-27" today={today} status={null} />);
    expect(screen.getByText(/up to date/i)).toBeInTheDocument();
  });

  it('reports the age of stale data in days', () => {
    render(<FreshnessCard latestDayKey="2026-08-22" today={today} status={null} />);
    expect(screen.getByText(/5 days ago/i)).toBeInTheDocument();
  });

  it('flags itself as stale so it can be styled differently', () => {
    const { container } = render(
      <FreshnessCard latestDayKey="2026-08-22" today={today} status={null} />,
    );
    expect(container.querySelector('[data-stale="true"]')).not.toBeNull();
  });

  it('is not stale at one day old', () => {
    const { container } = render(
      <FreshnessCard latestDayKey="2026-08-26" today={today} status={null} />,
    );
    expect(container.querySelector('[data-stale="true"]')).toBeNull();
  });

  it('is not stale at exactly the staleness boundary (two days old)', () => {
    const { container } = render(
      <FreshnessCard latestDayKey="2026-08-25" today={today} status={null} />,
    );
    expect(container.querySelector('[data-stale="true"]')).toBeNull();
  });

  it('prompts to reconnect when the integration needs reauthorisation', () => {
    render(
      <FreshnessCard
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: true, status: 'NEEDS_RECONNECT', lastSyncedAt: '2026-08-20T04:00:00' }}
      />,
    );
    expect(screen.getByText(/reconnect/i)).toBeInTheDocument();
  });

  it('flags itself as stale when reconnect is required even if the data is fresh', () => {
    const { container } = render(
      <FreshnessCard
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: true, status: 'NEEDS_RECONNECT', lastSyncedAt: '2026-08-20T04:00:00' }}
      />,
    );
    expect(container.querySelector('[data-stale="true"]')).not.toBeNull();
  });

  it('says the watch is not connected when it is not', () => {
    render(
      <FreshnessCard
        latestDayKey="2026-08-27"
        today={today}
        status={{ connected: false, status: null, lastSyncedAt: null }}
      />,
    );
    expect(screen.getByText(/not connected/i)).toBeInTheDocument();
  });

  it('handles having no data at all', () => {
    render(<FreshnessCard latestDayKey={null} today={today} status={null} />);
    expect(screen.getByText(/no data/i)).toBeInTheDocument();
  });

  it('flags sync status as unavailable when the sync call itself failed', () => {
    render(<FreshnessCard latestDayKey="2026-08-27" today={today} status={null} syncError />);
    expect(screen.getByText(/sync status unavailable/i)).toBeInTheDocument();
  });
});
