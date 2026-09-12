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

/**
 * The activity-type filter <select> now carries an <option> with the same text as
 * every row's type label (e.g. "Cycling" appears both as a row and as a filter
 * option), so a bare getByText/findByText('Cycling') is ambiguous. Match only the
 * row's own span instead.
 */
function rowType(label: string) {
  return (content: string, element: Element | null) =>
    content === label && element?.classList.contains('workout-row-type') === true;
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
    postForm: vi.fn().mockResolvedValue({}),
  } as unknown as ApiClient;
}

describe('WorkoutsPage', () => {
  it('renders a row per activity once the first page resolves', async () => {
    const api = apiWithPages({ 0: pageOf([activity()]) });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />);

    expect(await screen.findByText(rowType('Cycling'))).toBeInTheDocument();
    expect(screen.getByText('50 min')).toBeInTheDocument();
    expect(screen.getByText('18.4 km')).toBeInTheDocument();
  });

  it('shows an empty note when there is no history', async () => {
    const api = apiWithPages({ 0: pageOf([]) });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />);

    expect(await screen.findByText('No workouts logged yet.')).toBeInTheDocument();
  });

  it('appends the next page and hides Load more at the last page', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({
      0: pageOf([activity({ id: 'a1', activityType: 'CYCLING' })], 0, 2),
      1: pageOf([activity({ id: 'a2', activityType: 'RUNNING' })], 1, 2),
    });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />);

    await user.click(await screen.findByRole('button', { name: 'Load more' }));

    expect(await screen.findByText(rowType('Running'))).toBeInTheDocument();
    expect(screen.getByText(rowType('Cycling'))).toBeInTheDocument();
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
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />);

    expect(await screen.findByText('imported')).toBeInTheDocument();
    expect(screen.getByText('device')).toBeInTheDocument();
    expect(screen.queryByText('manual')).not.toBeInTheDocument();
  });

  it('opens the edit dialog when a row is tapped', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([activity()]) });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />);

    await user.click(await screen.findByRole('button', { name: /Cycling/ }));

    expect(await screen.findByRole('dialog', { name: 'Edit workout' })).toBeInTheDocument();
  });

  it('refetches page 0 after a successful edit', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([activity()]) });
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />);

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
    render(
      <WorkoutsPage
        api={api}
        createOpen
        onCreateClose={onCreateClose}
        importOpen={false}
        onImportClose={vi.fn()}
      />,
    );

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
    render(<WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />);

    expect(await screen.findByText('offline')).toBeInTheDocument();
  });

  it('opens the import dialog when the parent says so, and reports it closed', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([]) });
    const onImportClose = vi.fn();
    render(
      <WorkoutsPage
        api={api}
        createOpen={false}
        onCreateClose={vi.fn()}
        importOpen
        onImportClose={onImportClose}
      />,
    );

    expect(await screen.findByRole('dialog', { name: 'Import Fitbit CSV' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Close' }));

    expect(onImportClose).toHaveBeenCalled();
  });

  it('refetches page 0 after an import brings in new rows', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([]) });
    (api.postForm as ReturnType<typeof vi.fn>) = vi.fn().mockResolvedValue({
      fileName: 'export.csv',
      totalRows: 2,
      imported: 2,
      duplicatesSkipped: 0,
      failed: 0,
      errors: [],
    });
    render(
      <WorkoutsPage
        api={api}
        createOpen={false}
        onCreateClose={vi.fn()}
        importOpen
        onImportClose={vi.fn()}
      />,
    );
    await screen.findByRole('dialog', { name: 'Import Fitbit CSV' });

    const file = new File(['a,b\n1,2'], 'dailyActivity_merged.csv', { type: 'text/csv' });
    await user.upload(screen.getByLabelText(/csv file/i), file);
    await user.click(screen.getByRole('button', { name: 'Import' }));

    await waitFor(() => expect(api.postForm).toHaveBeenCalled());
    await waitFor(() => {
      const pageZeroCalls = (api.get as unknown as { mock: { calls: string[][] } }).mock.calls.filter(
        ([path]) => path.includes('page=0'),
      );
      expect(pageZeroCalls.length).toBe(2);
    });
  });

  it('refetches with the activityType filter applied, and clears it', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([activity()]) });
    render(
      <WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />,
    );
    await screen.findByText(rowType('Cycling'));

    await user.selectOptions(screen.getByLabelText('Type'), 'RUNNING');

    await waitFor(() => {
      const calls = (api.get as unknown as { mock: { calls: string[][] } }).mock.calls;
      expect(calls.some(([path]) => path.includes('activityType=RUNNING'))).toBe(true);
    });
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Clear filters' }));

    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument();
    await waitFor(() => {
      const calls = (api.get as unknown as { mock: { calls: string[][] } }).mock.calls;
      const last = calls.at(-1);
      expect(last?.[0]).not.toContain('activityType=');
    });
  });

  it('requests the from/to date filters together', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([activity()]) });
    render(
      <WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />,
    );
    await screen.findByText(rowType('Cycling'));

    await user.type(screen.getByLabelText('From'), '2026-08-01');
    await user.type(screen.getByLabelText('To'), '2026-08-31');

    await waitFor(() => {
      const calls = (api.get as unknown as { mock: { calls: string[][] } }).mock.calls;
      const last = calls.at(-1)?.[0] ?? '';
      expect(last).toContain('from=2026-08-01');
      expect(last).toContain('to=2026-08-31');
    });
  });

  it('shows a filter-aware empty state', async () => {
    const user = userEvent.setup();
    const api = apiWithPages({ 0: pageOf([activity()]) });
    render(
      <WorkoutsPage api={api} createOpen={false} onCreateClose={vi.fn()} importOpen={false} onImportClose={vi.fn()} />,
    );
    await screen.findByText(rowType('Cycling'));

    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue(pageOf([]));
    await user.selectOptions(screen.getByLabelText('Type'), 'YOGA');

    expect(await screen.findByText('No workouts match these filters.')).toBeInTheDocument();
  });
});
