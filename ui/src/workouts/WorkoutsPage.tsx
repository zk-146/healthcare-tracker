import { useEffect, useRef, useState } from 'react';
import type { ApiClient } from '../api/client';
import { listAllActivities } from '../api/endpoints';
import type { ActivityFilters, ActivityResponse, ActivitySource, ActivityType, Page } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import { useLoadable } from '../lib/useLoadable';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';
import { ImportCsvDialog } from './ImportCsvDialog';
import { TYPE_LABELS, WorkoutForm } from './WorkoutForm';

/** MANUAL rows carry no badge: that is the default and would be noise on every row. */
const SOURCE_BADGES: Partial<Record<ActivitySource, string>> = {
  CSV_IMPORT: 'imported',
  IOT: 'device',
};

/**
 * startedAt is a zoneless LocalDateTime. Parsing the date part by hand keeps the
 * browser from re-interpreting it as UTC and shifting the label by a day.
 */
function formatStartedAt(startedAt: string, locale = 'en-GB'): string {
  const [datePart, timePart = '00:00'] = startedAt.split('T');
  const [year, month, day] = datePart.split('-').map(Number);
  const label = new Date(year, month - 1, day).toLocaleDateString(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  });
  return `${label} · ${timePart.slice(0, 5)}`;
}

/** One metric per row, in the order the owner is most likely to care about. */
function metricOf(activity: ActivityResponse): string | null {
  if (activity.distanceKm !== null) {
    return `${activity.distanceKm} km`;
  }
  if (activity.steps !== null) {
    return `${activity.steps.toLocaleString('en-GB')} steps`;
  }
  if (activity.caloriesBurned !== null) {
    return `${Math.round(activity.caloriesBurned)} kcal`;
  }
  return null;
}

interface WorkoutsPageProps {
  api: ApiClient;
  /** Owned by App, because the trigger buttons live in the shared header. */
  createOpen: boolean;
  onCreateClose(): void;
  importOpen: boolean;
  onImportClose(): void;
}

export function WorkoutsPage({
  api,
  createOpen,
  onCreateClose,
  importOpen,
  onImportClose,
}: WorkoutsPageProps) {
  const [reloadKey, setReloadKey] = useState(0);
  const [rows, setRows] = useState<ActivityResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<string | null>(null);
  const [editing, setEditing] = useState<ActivityResponse | null>(null);
  const [filters, setFilters] = useState<ActivityFilters>({});

  // Bumped by every action that starts a fresh query (refetch/setFilter/clearFilters).
  // loadMore captures this before its request and only commits if it still matches when
  // the request resolves -- otherwise the filters/reloadKey changed mid-flight and the
  // response belongs to a query the user has since moved on from.
  const queryTokenRef = useRef(0);

  const first = useLoadable<Page<ActivityResponse>>(
    () => listAllActivities(api, 0, filters),
    [api, reloadKey, filters],
  );

  useEffect(() => {
    if (first.state === 'ready') {
      setRows(first.value.content);
      setPage(first.value.number);
      setTotalPages(first.value.totalPages);
    }
  }, [first]);

  /** Reset to a single fresh page 0 — the simplest correct thing after a mutation. */
  function refetch(): void {
    queryTokenRef.current += 1;
    setRows([]);
    setPage(0);
    setMoreError(null);
    setReloadKey((current) => current + 1);
  }

  async function loadMore(): Promise<void> {
    const token = queryTokenRef.current;
    const filtersAtRequest = filters;
    setLoadingMore(true);
    setMoreError(null);
    try {
      const next = await listAllActivities(api, page + 1, filtersAtRequest);
      if (queryTokenRef.current !== token) {
        // filters/reloadKey changed while this request was in flight (the user
        // switched a filter or triggered a refetch) — this page belongs to a query
        // that's no longer current, so applying it would corrupt the fresh row list.
        return;
      }
      setRows((current) => [...current, ...next.content]);
      setPage(next.number);
      setTotalPages(next.totalPages);
    } catch (cause: unknown) {
      if (queryTokenRef.current === token) {
        setMoreError(messageFor(cause));
      }
    } finally {
      if (queryTokenRef.current === token) {
        setLoadingMore(false);
      }
    }
  }

  const hasMore = page + 1 < totalPages;
  const hasActiveFilters =
    filters.activityType !== undefined || filters.from !== undefined || filters.to !== undefined;

  /** Mirrors refetch()'s clear-before-reload: without it, the stale (pre-filter) rows
   *  would sit alongside the loading skeleton until the filtered page resolves. */
  function setFilter<K extends keyof ActivityFilters>(key: K, value: string): void {
    queryTokenRef.current += 1;
    setRows([]);
    setMoreError(null);
    setFilters((current) => {
      const next = { ...current };
      if (value === '') {
        delete next[key];
      } else {
        next[key] = value as ActivityFilters[K];
      }
      return next;
    });
  }

  function clearFilters(): void {
    queryTokenRef.current += 1;
    setRows([]);
    setMoreError(null);
    setFilters({});
  }

  return (
    <>
      <div className="filter-row">
        <div className="field">
          <label className="field-label" htmlFor="filterType">
            Type
          </label>
          <select
            id="filterType"
            value={filters.activityType ?? ''}
            onChange={(event) => setFilter('activityType', event.target.value)}
          >
            <option value="">All types</option>
            {(Object.keys(TYPE_LABELS) as ActivityType[]).map((type) => (
              <option key={type} value={type}>
                {TYPE_LABELS[type]}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label className="field-label" htmlFor="filterFrom">
            From
          </label>
          <input
            id="filterFrom"
            type="date"
            value={filters.from ?? ''}
            onChange={(event) => setFilter('from', event.target.value)}
          />
        </div>

        <div className="field">
          <label className="field-label" htmlFor="filterTo">
            To
          </label>
          <input
            id="filterTo"
            type="date"
            value={filters.to ?? ''}
            onChange={(event) => setFilter('to', event.target.value)}
          />
        </div>

        {hasActiveFilters && (
          <button type="button" className="link-button" onClick={clearFilters}>
            Clear filters
          </button>
        )}
      </div>

      {first.state === 'loading' && <Skeleton height={200} />}
      {first.state === 'error' && <ErrorNote message={first.message} />}

      {first.state === 'ready' && rows.length === 0 && (
        <Card>
          <p className="empty-note">
            {hasActiveFilters ? 'No workouts match these filters.' : 'No workouts logged yet.'}
          </p>
        </Card>
      )}

      {rows.length > 0 && (
        <Card title="All workouts">
          <ul className="workout-list">
            {rows.map((row) => {
              const metric = metricOf(row);
              const badge = SOURCE_BADGES[row.source];
              return (
                <li key={row.id}>
                  <button type="button" className="workout-row" onClick={() => setEditing(row)}>
                    <span className="workout-row-main">
                      <span className="workout-row-type">{TYPE_LABELS[row.activityType]}</span>
                      <span className="workout-row-when">{formatStartedAt(row.startedAt)}</span>
                    </span>
                    <span className="workout-row-metrics">
                      <span>{row.durationMinutes ?? 0} min</span>
                      {metric !== null && <span>{metric}</span>}
                      {badge !== undefined && <span className="badge">{badge}</span>}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>

          {moreError !== null && <ErrorNote message={moreError} />}

          {hasMore && (
            <button
              type="button"
              className="link-button"
              disabled={loadingMore}
              onClick={() => void loadMore()}
            >
              {loadingMore ? 'Loading…' : 'Load more'}
            </button>
          )}
        </Card>
      )}

      {createOpen && (
        <WorkoutForm api={api} onClose={onCreateClose} onSaved={refetch} />
      )}

      {editing !== null && (
        <WorkoutForm
          api={api}
          initial={editing}
          onClose={() => setEditing(null)}
          onSaved={refetch}
        />
      )}

      {importOpen && (
        <ImportCsvDialog api={api} onClose={onImportClose} onImported={refetch} />
      )}
    </>
  );
}
