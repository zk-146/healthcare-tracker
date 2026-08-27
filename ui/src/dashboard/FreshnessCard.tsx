import type { GoogleHealthStatusResponse } from '../api/types';
import { daysSince, formatDayLabel } from '../lib/days';
import { Card } from '../ui/Card';

interface FreshnessCardProps {
  latestDayKey: string | null;
  today: Date;
  status: GoogleHealthStatusResponse | null;
}

/** More than this many days behind and the card styles itself as a warning. */
const STALE_AFTER_DAYS = 2;

function describeAge(latestDayKey: string | null, today: Date): string {
  if (latestDayKey === null) {
    return 'No data imported yet';
  }
  const age = daysSince(latestDayKey, today);
  if (age <= 0) {
    return 'Up to date';
  }
  if (age === 1) {
    return `Last data: ${formatDayLabel(latestDayKey)} (yesterday)`;
  }
  return `Last data: ${formatDayLabel(latestDayKey)} (${age} days ago)`;
}

function describeConnection(status: GoogleHealthStatusResponse | null): string | null {
  if (status === null) {
    return null;
  }
  if (!status.connected) {
    return 'Watch not connected';
  }
  if (status.status === 'NEEDS_RECONNECT') {
    return 'Reconnect required';
  }
  return 'Watch connected';
}

export function FreshnessCard({ latestDayKey, today, status }: FreshnessCardProps) {
  const age = latestDayKey === null ? Number.POSITIVE_INFINITY : daysSince(latestDayKey, today);
  const stale = age > STALE_AFTER_DAYS || status?.status === 'NEEDS_RECONNECT';
  const connection = describeConnection(status);

  return (
    <Card>
      <div className="freshness" data-stale={stale}>
        <span>{describeAge(latestDayKey, today)}</span>
        {connection !== null && <span className="day-row-metrics">{connection}</span>}
      </div>
    </Card>
  );
}
