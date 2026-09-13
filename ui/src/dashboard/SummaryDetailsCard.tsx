import { useEffect, useRef, useState } from 'react';
import { type ApiClient } from '../api/client';
import { getDigest, getSummaryFor } from '../api/endpoints';
import type { DigestResponse, SummaryPeriod } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import type { Loadable } from '../lib/useLoadable';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';
import { SummaryBody } from './SummaryBody';

const PERIOD_LABELS: Record<SummaryPeriod, string> = {
  daily: 'Day',
  weekly: 'Week',
  monthly: 'Month',
};

const PERIODS: SummaryPeriod[] = ['daily', 'weekly', 'monthly'];

interface SummaryDetailsCardProps {
  api: ApiClient;
}

export function SummaryDetailsCard({ api }: SummaryDetailsCardProps) {
  const [period, setPeriod] = useState<SummaryPeriod>('weekly');
  const [digest, setDigest] = useState<Loadable<DigestResponse> | null>(null);

  // Tracks the *current* period so an in-flight loadDigest() can tell, at resolution
  // time, whether the user has since switched away from the period it was requested
  // for. A plain closure variable can't do this: `period` inside loadDigest is fixed
  // to the value at the render that created the closure, so it never changes even
  // after the user switches periods mid-request.
  const periodRef = useRef(period);
  periodRef.current = period;

  // A digest fetched for one period is stale (and possibly costly to regenerate) once the
  // period changes, so drop it rather than showing last period's recap under a new label.
  useEffect(() => {
    setDigest(null);
  }, [period]);

  async function loadDigest(): Promise<void> {
    const requestedPeriod = period;
    setDigest({ state: 'loading' });
    try {
      const value = await getDigest(api, requestedPeriod);
      if (periodRef.current !== requestedPeriod) {
        // The user switched periods before this resolved; the effect above already
        // cleared `digest` for the new period, so applying this stale result would
        // show one period's recap mislabeled as another's.
        return;
      }
      setDigest({ state: 'ready', value });
    } catch (cause: unknown) {
      if (periodRef.current === requestedPeriod) {
        setDigest({ state: 'error', message: messageFor(cause) });
      }
    }
  }

  return (
    <Card title="Summary">
      <div className="segmented" role="group" aria-label="Summary period">
        {PERIODS.map((candidate) => (
          <button
            key={candidate}
            type="button"
            className="segmented-button"
            aria-pressed={period === candidate}
            onClick={() => setPeriod(candidate)}
          >
            {PERIOD_LABELS[candidate]}
          </button>
        ))}
      </div>

      <SummaryBody load={() => getSummaryFor(api, period)} loadKey={period}>
        {digest === null && (
          <button type="button" className="link-button" onClick={() => void loadDigest()}>
            Generate AI recap
          </button>
        )}
        {digest?.state === 'loading' && <Skeleton height={60} />}
        {digest?.state === 'error' && <ErrorNote message={digest.message} />}
        {digest?.state === 'ready' && (
          <p className="digest-text">
            {digest.value.digest}
            {!digest.value.available && ' (AI recap unavailable right now.)'}
          </p>
        )}
      </SummaryBody>
    </Card>
  );
}
