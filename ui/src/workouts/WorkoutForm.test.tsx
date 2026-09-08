import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import type { ActivityResponse } from '../api/types';
import { WorkoutForm } from './WorkoutForm';

const now = new Date(2026, 8, 8, 10, 0);

function fakeApi(overrides: Partial<Record<'post' | 'put' | 'del', unknown>> = {}): ApiClient {
  return {
    get: vi.fn(),
    post: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
    del: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  } as unknown as ApiClient;
}

const existing: ActivityResponse = {
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
};

describe('WorkoutForm — create mode', () => {
  it('renders the create title and hides the optional fields', () => {
    render(<WorkoutForm api={fakeApi()} onClose={vi.fn()} onSaved={vi.fn()} now={now} />);

    expect(screen.getByRole('dialog', { name: 'Log workout' })).toBeInTheDocument();
    expect(screen.getByLabelText('Duration (minutes)')).toHaveValue(null);
    expect(screen.queryByLabelText('Distance (km)')).not.toBeInTheDocument();
  });

  it('reveals the optional fields behind the More details toggle', async () => {
    const user = userEvent.setup();
    render(<WorkoutForm api={fakeApi()} onClose={vi.fn()} onSaved={vi.fn()} now={now} />);

    await user.click(screen.getByRole('button', { name: 'More details' }));

    expect(screen.getByLabelText('Distance (km)')).toBeInTheDocument();
    expect(screen.getByLabelText('Notes')).toBeInTheDocument();
  });

  it('blocks submit and shows a field error when the client validation fails', async () => {
    const user = userEvent.setup();
    const api = fakeApi();
    render(<WorkoutForm api={api} onClose={vi.fn()} onSaved={vi.fn()} now={now} />);

    await user.click(screen.getByRole('button', { name: 'Save workout' }));

    expect(await screen.findByText('Duration is required')).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('posts the workout, then reports saved and closes', async () => {
    const user = userEvent.setup();
    const api = fakeApi();
    const onSaved = vi.fn();
    const onClose = vi.fn();
    render(<WorkoutForm api={api} onClose={onClose} onSaved={onSaved} now={now} />);

    await user.selectOptions(screen.getByLabelText('Activity'), 'RUNNING');
    await user.type(screen.getByLabelText('Duration (minutes)'), '45');
    await user.click(screen.getByRole('button', { name: 'Save workout' }));

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(api.post).toHaveBeenCalledWith('/api/v1/activities', {
      activityType: 'RUNNING',
      source: 'MANUAL',
      startedAt: '2026-09-08T10:00:00',
      durationMinutes: 45,
    });
    expect(onClose).toHaveBeenCalled();
  });

  it('maps a server 400 details map onto the individual fields', async () => {
    const user = userEvent.setup();
    const api = fakeApi({
      post: vi.fn().mockRejectedValue(
        new ApiError(400, {
          error: 'Validation failed',
          details: { durationMinutes: 'Duration cannot exceed 1440 minutes (24 hours)' },
        }),
      ),
    });
    render(<WorkoutForm api={api} onClose={vi.fn()} onSaved={vi.fn()} now={now} />);

    await user.type(screen.getByLabelText('Duration (minutes)'), '30');
    await user.click(screen.getByRole('button', { name: 'Save workout' }));

    expect(
      await screen.findByText('Duration cannot exceed 1440 minutes (24 hours)'),
    ).toBeInTheDocument();
  });

  it('disables the save button while the request is in flight', async () => {
    const user = userEvent.setup();
    let release = (): void => {};
    const api = fakeApi({
      post: vi.fn(() => new Promise((resolve) => {
        release = () => resolve({});
      })),
    });
    render(<WorkoutForm api={api} onClose={vi.fn()} onSaved={vi.fn()} now={now} />);

    await user.type(screen.getByLabelText('Duration (minutes)'), '30');
    await user.click(screen.getByRole('button', { name: 'Save workout' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled());
    release();
  });

  it('closes on Escape', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<WorkoutForm api={fakeApi()} onClose={onClose} onSaved={vi.fn()} now={now} />);

    await user.keyboard('{Escape}');

    expect(onClose).toHaveBeenCalled();
  });

  it('routes an unmatched 400 detail key to the banner, not a field error', async () => {
    const user = userEvent.setup();
    const api = fakeApi({
      post: vi.fn().mockRejectedValue(
        new ApiError(400, {
          error: 'Validation failed',
          details: { someClassLevelRule: 'Start and end times are inconsistent' },
        }),
      ),
    });
    render(<WorkoutForm api={api} onClose={vi.fn()} onSaved={vi.fn()} now={now} />);

    await user.type(screen.getByLabelText('Duration (minutes)'), '30');
    await user.click(screen.getByRole('button', { name: 'Save workout' }));

    expect(
      await screen.findByText('Start and end times are inconsistent'),
    ).toBeInTheDocument();
    expect(document.querySelector('.field-error')).not.toBeInTheDocument();
  });

  it('cycles Tab and Shift+Tab focus within the dialog without escaping it', async () => {
    const user = userEvent.setup();
    render(<WorkoutForm api={fakeApi()} onClose={vi.fn()} onSaved={vi.fn()} now={now} />);

    const dialog = screen.getByRole('dialog');
    const focusable = dialog.querySelectorAll('select, input, textarea, button');
    const first = focusable[0] as HTMLElement;
    const last = focusable[focusable.length - 1] as HTMLElement;

    first.focus();
    await user.tab({ shift: true });
    expect(document.activeElement).toBe(last);

    last.focus();
    await user.tab();
    expect(document.activeElement).toBe(first);
  });
});

describe('WorkoutForm — edit mode', () => {
  it('prefills from the activity and pre-expands the details section', () => {
    render(
      <WorkoutForm api={fakeApi()} initial={existing} onClose={vi.fn()} onSaved={vi.fn()} now={now} />,
    );

    expect(screen.getByRole('dialog', { name: 'Edit workout' })).toBeInTheDocument();
    expect(screen.getByLabelText('Activity')).toHaveValue('CYCLING');
    expect(screen.getByLabelText('Started at')).toHaveValue('2026-09-07T18:15');
    expect(screen.getByLabelText('Duration (minutes)')).toHaveValue(50);
    expect(screen.getByLabelText('Distance (km)')).toHaveValue(18.4);
  });

  it('leaves the details section collapsed when no optional field is set', () => {
    render(
      <WorkoutForm
        api={fakeApi()}
        initial={{ ...existing, distanceKm: null }}
        onClose={vi.fn()}
        onSaved={vi.fn()}
        now={now}
      />,
    );

    expect(screen.queryByLabelText('Distance (km)')).not.toBeInTheDocument();
  });

  it('puts the update to the activity id', async () => {
    const user = userEvent.setup();
    const api = fakeApi();
    render(
      <WorkoutForm api={api} initial={existing} onClose={vi.fn()} onSaved={vi.fn()} now={now} />,
    );

    await user.click(screen.getByRole('button', { name: 'Save workout' }));

    await waitFor(() =>
      expect(api.put).toHaveBeenCalledWith('/api/v1/activities/a1', {
        activityType: 'CYCLING',
        source: 'MANUAL',
        startedAt: '2026-09-07T18:15:00',
        durationMinutes: 50,
        distanceKm: 18.4,
      }),
    );
  });

  it('shows the conflict banner on a 409 instead of a raw error', async () => {
    const user = userEvent.setup();
    const api = fakeApi({
      put: vi.fn().mockRejectedValue(new ApiError(409, { error: 'Optimistic lock' })),
    });
    const onClose = vi.fn();
    render(
      <WorkoutForm api={api} initial={existing} onClose={onClose} onSaved={vi.fn()} now={now} />,
    );

    await user.click(screen.getByRole('button', { name: 'Save workout' }));

    expect(
      await screen.findByText(
        'This workout changed on another device. Close and reopen to see the latest.',
      ),
    ).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('requires a second click to confirm a delete', async () => {
    const user = userEvent.setup();
    const api = fakeApi();
    const onSaved = vi.fn();
    render(
      <WorkoutForm api={api} initial={existing} onClose={vi.fn()} onSaved={onSaved} now={now} />,
    );

    await user.click(screen.getByRole('button', { name: 'Delete this workout' }));
    expect(api.del).not.toHaveBeenCalled();
    expect(screen.getByText('Delete this workout?')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Confirm' }));

    await waitFor(() => expect(api.del).toHaveBeenCalledWith('/api/v1/activities/a1'));
    expect(onSaved).toHaveBeenCalled();
  });

  it('backs out of the delete confirm on Cancel', async () => {
    const user = userEvent.setup();
    const api = fakeApi();
    render(
      <WorkoutForm api={api} initial={existing} onClose={vi.fn()} onSaved={vi.fn()} now={now} />,
    );

    await user.click(screen.getByRole('button', { name: 'Delete this workout' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(api.del).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Delete this workout' })).toBeInTheDocument();
  });

  it('offers no delete in create mode', () => {
    render(<WorkoutForm api={fakeApi()} onClose={vi.fn()} onSaved={vi.fn()} now={now} />);

    expect(screen.queryByRole('button', { name: 'Delete this workout' })).not.toBeInTheDocument();
  });
});
