import { useEffect, useRef, useState } from 'react';
import { type ApiClient } from '../api/client';
import { getDigest, getSummaryFor, getSummaryRange } from '../api/endpoints';
import type { DigestResponse, SummaryPeriod } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import { toDayKey } from '../lib/days';
import type { Loadable } from '../lib/useLoadable';
import { useToday } from '../lib/useToday';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';
import { defaultRange, validateRange, type DateRange } from './range';
import { SummaryBody } from './SummaryBody';

type Selection = SummaryPeriod | 'custom';

const SELECTION_LABELS: Record<Selection, string> = {
  daily: 'Day',
  weekly: 'Week',
  monthly: 'Month',
  custom: 'Custom',
};

const SELECTIONS: Selection[] = ['daily', 'weekly', 'monthly', 'custom'];

interface SummaryDetailsCardProps {
  api: ApiClient;
  /** Injectable for tests; the default custom range ends on this day. */
  now?: Date;
}

export function SummaryDetailsCard({ api, now }: SummaryDetailsCardProps) {
  const [selection, setSelection] = useState<Selection>('weekly');
  // Kept across selection changes so flipping to Week and back doesn't lose the dates.
  // useToday rolls over at midnight even if nothing re-renders the card; an injected
  // `now` (tests) takes precedence.
  const liveToday = useToday();
  const today = now === undefined ? liveToday : toDayKey(now);
  const [range, setRange] = useState<DateRange>(() => defaultRange(now ?? new Date()));
  const [digest, setDigest] = useState<Loadable<DigestResponse> | null>(null);

  // Tracks the *current* selection so an in-flight loadDigest() can tell, at resolution
  // time, whether the user has since switched away from the period it was requested
  // for. A plain closure variable can't do this: the value inside loadDigest is fixed
  // at the render that created the closure, so it never changes even after the user
  // switches mid-request.
  const selectionRef = useRef(selection);
  selectionRef.current = selection;

  // A digest fetched for one period is stale (and possibly costly to regenerate) once the
  // selection changes, so drop it rather than showing last period's recap under a new label.
  useEffect(() => {
    setDigest(null);
  }, [selection]);

  async function loadDigest(requestedPeriod: SummaryPeriod): Promise<void> {
    setDigest({ state: 'loading' });
    try {
      const value = await getDigest(api, requestedPeriod);
      if (selectionRef.current !== requestedPeriod) {
        // The user switched before this resolved; the effect above already cleared
        // `digest`, so applying this stale result would mislabel one period's recap.
        return;
      }
      setDigest({ state: 'ready', value });
    } catch (cause: unknown) {
      if (selectionRef.current === requestedPeriod) {
        setDigest({ state: 'error', message: messageFor(cause) });
      }
    }
  }

  function setRangeField(key: keyof DateRange, value: string): void {
    setRange((current) => ({ ...current, [key]: value }));
  }

  function renderBody() {
    if (selection === 'custom') {
      const rangeError = validateRange(range.from, range.to, today);
      if (rangeError !== null) {
        // No SummaryBody means no request: the range is fixed client-side first.
        return <ErrorNote message={rangeError} />;
      }
      // No AI recap here: /summary/digest only understands daily/weekly/monthly.
      return (
        <SummaryBody
          load={() => getSummaryRange(api, range.from, range.to)}
          loadKey={`custom:${range.from}:${range.to}`}
        />
      );
    }

    const period = selection;
    return (
      <SummaryBody load={() => getSummaryFor(api, period)} loadKey={period}>
        {digest === null && (
          <button type="button" className="link-button" onClick={() => void loadDigest(period)}>
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
    );
  }

  return (
    <Card title="Summary">
      <div className="segmented" role="group" aria-label="Summary period">
        {SELECTIONS.map((candidate) => (
          <button
            key={candidate}
            type="button"
            className="segmented-button"
            aria-pressed={selection === candidate}
            onClick={() => setSelection(candidate)}
          >
            {SELECTION_LABELS[candidate]}
          </button>
        ))}
      </div>

      {selection === 'custom' && (
        <div className="filter-row">
          <div className="field">
            <label className="field-label" htmlFor="summaryFrom">
              From
            </label>
            <input
              id="summaryFrom"
              type="date"
              max={today}
              value={range.from}
              onChange={(event) => setRangeField('from', event.target.value)}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="summaryTo">
              To
            </label>
            <input
              id="summaryTo"
              type="date"
              max={today}
              value={range.to}
              onChange={(event) => setRangeField('to', event.target.value)}
            />
          </div>
        </div>
      )}

      {renderBody()}
    </Card>
  );
}
