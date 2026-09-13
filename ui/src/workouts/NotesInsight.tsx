import { useState } from 'react';
import type { ApiClient } from '../api/client';
import { getNotesInsight } from '../api/endpoints';
import type { NotesInsightResponse } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import type { Loadable } from '../lib/useLoadable';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';

function capitalise(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function painLine(value: NotesInsightResponse): string {
  if (value.painMentioned !== true) {
    return 'No pain mentioned';
  }
  return value.painDescription === null ? 'Pain mentioned' : `Pain mentioned: ${value.painDescription}`;
}

interface NotesInsightProps {
  api: ApiClient;
  activityId: string;
}

/**
 * On-demand only: each analysis is an LLM call, possibly billed to the user's own
 * DeepSeek key, so nothing is fetched until the button is pressed.
 */
export function NotesInsight({ api, activityId }: NotesInsightProps) {
  const [insight, setInsight] = useState<Loadable<NotesInsightResponse> | null>(null);

  async function analyze(): Promise<void> {
    setInsight({ state: 'loading' });
    try {
      const value = await getNotesInsight(api, activityId);
      setInsight({ state: 'ready', value });
    } catch (cause: unknown) {
      setInsight({ state: 'error', message: messageFor(cause) });
    }
  }

  return (
    <div aria-live="polite">
      {insight === null && (
        <button type="button" className="link-button" onClick={() => void analyze()}>
          Analyze notes
        </button>
      )}
      {insight?.state === 'loading' && <Skeleton height={60} />}
      {insight?.state === 'error' && <ErrorNote message={insight.message} />}
      {insight?.state === 'ready' && !insight.value.available && (
        <p className="digest-text">{insight.value.message}</p>
      )}
      {insight?.state === 'ready' && insight.value.available && (
        <>
          <p className="digest-text">{`Mood: ${capitalise(insight.value.mood ?? 'unknown')}`}</p>
          <p className="digest-text">{painLine(insight.value)}</p>
        </>
      )}
    </div>
  );
}
