import { useState } from 'react';
import { type ApiClient } from '../api/client';
import { disconnectGoogleHealth, getGoogleHealthConnectUrl } from '../api/endpoints';
import type { GoogleHealthStatusResponse } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import { daysSince, formatDayLabel } from '../lib/days';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';

interface FreshnessCardProps {
  api: ApiClient;
  latestDayKey: string | null;
  today: Date;
  status: GoogleHealthStatusResponse | null;
  syncError?: boolean;
  /** Fires after a successful disconnect, so the parent can refetch the status. */
  onDisconnected(): void;
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

function describeConnection(
  status: GoogleHealthStatusResponse | null,
  syncError: boolean,
): string | null {
  if (syncError) {
    return 'Sync status unavailable';
  }
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

export function FreshnessCard({
  api,
  latestDayKey,
  today,
  status,
  syncError = false,
  onDisconnected,
}: FreshnessCardProps) {
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [opened, setOpened] = useState(false);

  const age = latestDayKey === null ? Number.POSITIVE_INFINITY : daysSince(latestDayKey, today);
  const needsReconnect = status?.status === 'NEEDS_RECONNECT';
  const stale = age > STALE_AFTER_DAYS || needsReconnect;
  const connection = describeConnection(status, syncError);

  async function handleConnect(): Promise<void> {
    setBusy(true);
    setActionError(null);
    try {
      const { authorizationUrl } = await getGoogleHealthConnectUrl(api);
      window.open(authorizationUrl, '_blank', 'noopener,noreferrer');
      setOpened(true);
    } catch (cause: unknown) {
      setActionError(messageFor(cause));
    } finally {
      setBusy(false);
    }
  }

  async function handleDisconnect(): Promise<void> {
    setBusy(true);
    setActionError(null);
    try {
      await disconnectGoogleHealth(api);
      setOpened(false);
      onDisconnected();
    } catch (cause: unknown) {
      setActionError(messageFor(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <div className="freshness" data-stale={stale}>
        <span>{describeAge(latestDayKey, today)}</span>
        {connection !== null && <span className="day-row-metrics">{connection}</span>}
      </div>

      {actionError !== null && <ErrorNote message={actionError} />}

      {opened && (
        <p className="empty-note">
          Opened in a new tab — approve access there, then come back and reload.
        </p>
      )}

      {status !== null && !status.connected && (
        <button type="button" className="link-button" disabled={busy} onClick={() => void handleConnect()}>
          {busy ? 'Connecting…' : 'Connect watch'}
        </button>
      )}

      {status !== null && status.connected && needsReconnect && (
        <button type="button" className="link-button" disabled={busy} onClick={() => void handleConnect()}>
          {busy ? 'Connecting…' : 'Reconnect'}
        </button>
      )}

      {status !== null && status.connected && (
        <button type="button" className="link-button" disabled={busy} onClick={() => void handleDisconnect()}>
          {busy ? 'Disconnecting…' : 'Disconnect'}
        </button>
      )}
    </Card>
  );
}
