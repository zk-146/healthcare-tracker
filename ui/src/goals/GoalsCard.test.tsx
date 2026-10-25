import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import type { GoalProgress, GoalResponse } from '../api/types';
import { GoalsCard } from './GoalsCard';

function stubApi(overrides: Partial<ApiClient> = {}): ApiClient {
  return {
    get: vi.fn().mockResolvedValue([]),
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
    postForm: vi.fn(),
    ...overrides,
  } as unknown as ApiClient;
}

/** Overrides allow a partial progress block so each test states only the fields it cares about. */
type GoalOverrides = Partial<Omit<GoalResponse, 'progress'>> & {
  progress?: Partial<GoalProgress>;
};

function goal(overrides: GoalOverrides = {}): GoalResponse {
  const base: GoalResponse = {
    id: 'g1',
    metric: 'MINUTES',
    period: 'WEEKLY',
    targetValue: 150,
    unit: 'minutes',
    createdAt: '2026-09-01T10:00:00',
    updatedAt: '2026-09-01T10:00:00',
    progress: {
      from: '2026-09-07',
      to: '2026-09-09',
      currentValue: 60,
      percentComplete: 40,
      achieved: false,
      daysElapsed: 3,
      daysTotal: 7,
      daysRemaining: 4,
      expectedByNow: 64.3,
      onTrack: false,
    },
  };
  return { ...base, ...overrides, progress: { ...base.progress, ...overrides.progress } };
}

function row(title: string): HTMLElement {
  return screen.getByText(title).closest('li') as HTMLElement;
}

describe('GoalsCard', () => {
  it('says so plainly when there are no goals yet', async () => {
    render(<GoalsCard api={stubApi()} />);

    expect(await screen.findByText('No goals yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /add goal/i })).toBeEnabled();
  });

  it('lists existing goals with their targets', async () => {
    render(<GoalsCard api={stubApi({ get: vi.fn().mockResolvedValue([goal()]) })} />);

    await screen.findByText('Weekly active minutes');
    const weekly = row('Weekly active minutes');
    expect(within(weekly).getByText('150 minutes')).toBeInTheDocument();
  });

  it('creates a goal from the form and reloads the list', async () => {
    const get = vi.fn().mockResolvedValue([]);
    const post = vi.fn().mockResolvedValue(goal());
    render(<GoalsCard api={stubApi({ get, post })} />);

    await screen.findByText('No goals yet.');

    await userEvent.selectOptions(screen.getByLabelText(/measure/i), 'DISTANCE_KM');
    await userEvent.selectOptions(screen.getByLabelText(/period/i), 'MONTHLY');
    await userEvent.type(screen.getByLabelText(/target/i), '80');
    await userEvent.click(screen.getByRole('button', { name: /add goal/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith('/api/v1/goals', {
        metric: 'DISTANCE_KM',
        period: 'MONTHLY',
        targetValue: 80,
      });
    });
    // Two calls: the initial load plus the reload the successful create triggers.
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('relabels the target field with the selected measure unit', async () => {
    render(<GoalsCard api={stubApi()} />);
    await screen.findByText('No goals yet.');

    expect(screen.getByLabelText('Target (minutes)')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText(/measure/i), 'STEPS');

    expect(screen.getByLabelText('Target (steps)')).toBeInTheDocument();
  });

  it('blocks a duplicate measure and period before any request is made', async () => {
    const post = vi.fn();
    render(<GoalsCard api={stubApi({ get: vi.fn().mockResolvedValue([goal()]), post })} />);

    await screen.findByText('Weekly active minutes');

    // The form already defaults to weekly minutes, which this user holds.
    expect(
      screen.getByText(/you already have a weekly active minutes goal/i),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /add goal/i })).toBeDisabled();
    expect(post).not.toHaveBeenCalled();
  });

  it('re-enables the form once a free measure or period is chosen', async () => {
    render(<GoalsCard api={stubApi({ get: vi.fn().mockResolvedValue([goal()]) })} />);
    await screen.findByText('Weekly active minutes');

    await userEvent.selectOptions(screen.getByLabelText(/period/i), 'MONTHLY');

    expect(screen.getByRole('button', { name: /add goal/i })).toBeEnabled();
  });

  it('rejects a bad target locally without calling the API', async () => {
    const post = vi.fn();
    render(<GoalsCard api={stubApi({ post })} />);
    await screen.findByText('No goals yet.');

    await userEvent.type(screen.getByLabelText(/target/i), '0');
    await userEvent.click(screen.getByRole('button', { name: /add goal/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Target must be greater than 0');
    expect(post).not.toHaveBeenCalled();
  });

  it("surfaces the backend's field error on the target input", async () => {
    const post = vi
      .fn()
      .mockRejectedValue(
        new ApiError(400, {
          error: 'Validation failed',
          details: { targetValue: 'Target cannot exceed 10080 minutes for a weekly goal' },
        }),
      );
    render(<GoalsCard api={stubApi({ post })} />);
    await screen.findByText('No goals yet.');

    await userEvent.type(screen.getByLabelText(/target/i), '500');
    await userEvent.click(screen.getByRole('button', { name: /add goal/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot exceed 10080 minutes/);
  });

  it("surfaces a 409 from the server as a banner using the server's own wording", async () => {
    const post = vi
      .fn()
      .mockRejectedValue(new ApiError(409, { error: 'A weekly minutes goal already exists.' }));
    render(<GoalsCard api={stubApi({ post })} />);
    await screen.findByText('No goals yet.');

    await userEvent.type(screen.getByLabelText(/target/i), '150');
    await userEvent.click(screen.getByRole('button', { name: /add goal/i }));

    expect(await screen.findByText('A weekly minutes goal already exists.')).toBeInTheDocument();
  });

  it('changes an existing target through the update endpoint', async () => {
    const get = vi.fn().mockResolvedValue([goal()]);
    const put = vi.fn().mockResolvedValue(goal({ targetValue: 200 }));
    render(<GoalsCard api={stubApi({ get, put })} />);

    await screen.findByText('Weekly active minutes');
    await userEvent.click(screen.getByRole('button', { name: /change target/i }));

    const input = screen.getByLabelText('New target (minutes)');
    await userEvent.clear(input);
    await userEvent.type(input, '200');
    await userEvent.click(screen.getByRole('button', { name: /save target/i }));

    await waitFor(() => {
      expect(put).toHaveBeenCalledWith('/api/v1/goals/g1', { targetValue: 200 });
    });
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('validates an edited target before sending it', async () => {
    const put = vi.fn();
    render(<GoalsCard api={stubApi({ get: vi.fn().mockResolvedValue([goal()]), put })} />);

    await screen.findByText('Weekly active minutes');
    await userEvent.click(screen.getByRole('button', { name: /change target/i }));

    const input = screen.getByLabelText('New target (minutes)');
    await userEvent.clear(input);
    await userEvent.type(input, '99999');
    await userEvent.click(screen.getByRole('button', { name: /save target/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot exceed/);
    expect(put).not.toHaveBeenCalled();
  });

  it('requires confirmation before removing a goal', async () => {
    const get = vi.fn().mockResolvedValue([goal()]);
    const del = vi.fn().mockResolvedValue(undefined);
    render(<GoalsCard api={stubApi({ get, del })} />);

    await screen.findByText('Weekly active minutes');
    await userEvent.click(screen.getByRole('button', { name: /^remove$/i }));

    expect(del).not.toHaveBeenCalled();
    expect(screen.getByText('Remove this goal?')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /confirm/i }));

    await waitFor(() => expect(del).toHaveBeenCalledWith('/api/v1/goals/g1'));
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('abandons a removal when cancelled', async () => {
    const del = vi.fn();
    render(<GoalsCard api={stubApi({ get: vi.fn().mockResolvedValue([goal()]), del })} />);

    await screen.findByText('Weekly active minutes');
    await userEvent.click(screen.getByRole('button', { name: /^remove$/i }));
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(del).not.toHaveBeenCalled();
    expect(screen.queryByText('Remove this goal?')).not.toBeInTheDocument();
  });

  it('hides the add form at the goal ceiling', async () => {
    const many = Array.from({ length: 10 }, (_, index) =>
      goal({ id: `g${index}`, targetValue: 100 + index }),
    );
    render(<GoalsCard api={stubApi({ get: vi.fn().mockResolvedValue(many) })} />);

    expect(await screen.findByText(/maximum of 10 goals/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /add goal/i })).not.toBeInTheDocument();
  });

  it('shows an error note when the list cannot be loaded', async () => {
    const get = vi.fn().mockRejectedValue(new ApiError(500, { error: 'Internal error' }));
    render(<GoalsCard api={stubApi({ get })} />);

    expect(await screen.findByText(/internal error/i)).toBeInTheDocument();
  });
});
