import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';

const signOut = vi.fn().mockResolvedValue(undefined);

vi.mock('./auth/useAuth', () => ({
  useAuth: () => ({
    api: {},
    isAuthenticated: true,
    signIn: vi.fn(),
    signOut,
  }),
}));

vi.mock('./dashboard/DashboardPage', () => ({
  DashboardPage: () => <div>dashboard stub</div>,
}));

vi.mock('./workouts/WorkoutsPage', () => ({
  WorkoutsPage: ({ createOpen }: { createOpen: boolean }) => (
    <div>workouts stub {createOpen ? 'creating' : 'idle'}</div>
  ),
}));

describe('App shell', () => {
  it('starts on the Activity tab', () => {
    render(<App />);

    expect(screen.getByText('dashboard stub')).toBeInTheDocument();
    expect(screen.queryByText(/workouts stub/)).not.toBeInTheDocument();
  });

  it('swaps to the Workouts tab', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('tab', { name: 'Workouts' }));

    expect(screen.getByText(/workouts stub/)).toBeInTheDocument();
    expect(screen.queryByText('dashboard stub')).not.toBeInTheDocument();
  });

  it('shows the log-workout trigger only on the Workouts tab, and it opens the form', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(screen.queryByRole('button', { name: '＋ Log workout' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Workouts' }));
    await user.click(screen.getByRole('button', { name: '＋ Log workout' }));

    expect(screen.getByText(/workouts stub creating/)).toBeInTheDocument();
  });

  it('signs out from the shared header', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(signOut).toHaveBeenCalled();
  });
});
