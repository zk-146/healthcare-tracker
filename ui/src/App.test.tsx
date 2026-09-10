import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

const signOut = vi.fn().mockResolvedValue(undefined);
const clearSession = vi.fn();
let isAuthenticated = true;

vi.mock('./auth/useAuth', () => ({
  useAuth: () => ({
    api: {},
    isAuthenticated,
    signIn: vi.fn(),
    signUp: vi.fn(),
    signOut,
    clearSession,
  }),
}));

vi.mock('./dashboard/DashboardPage', () => ({
  DashboardPage: () => <div>dashboard stub</div>,
}));

vi.mock('./workouts/WorkoutsPage', () => ({
  WorkoutsPage: ({ createOpen, importOpen }: { createOpen: boolean; importOpen: boolean }) => (
    <div>
      workouts stub {createOpen ? 'creating' : 'idle'} {importOpen ? 'importing' : 'not-importing'}
    </div>
  ),
}));

vi.mock('./profile/ProfilePage', () => ({
  ProfilePage: ({ onAccountDeleted }: { onAccountDeleted: () => void }) => (
    <div>
      profile stub
      <button type="button" onClick={onAccountDeleted}>
        stub delete
      </button>
    </div>
  ),
}));

describe('App shell', () => {
  beforeEach(() => {
    isAuthenticated = true;
  });

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

  it('shows the import-CSV trigger only on the Workouts tab, and it opens the dialog', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(screen.queryByRole('button', { name: 'Import CSV' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Workouts' }));
    await user.click(screen.getByRole('button', { name: 'Import CSV' }));

    expect(screen.getByText(/workouts stub idle importing/)).toBeInTheDocument();
  });

  it('signs out from the shared header', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(signOut).toHaveBeenCalled();
  });

  it('swaps to the Profile tab and clears the session on account deletion', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('tab', { name: 'Profile' }));
    expect(screen.getByText(/profile stub/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'stub delete' }));
    expect(clearSession).toHaveBeenCalled();
  });
});

describe('App shell (signed out)', () => {
  beforeEach(() => {
    isAuthenticated = false;
  });

  it('shows the login form and switches to registration and back', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /create an account/i }));
    expect(screen.getByRole('button', { name: 'Create account' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /already have an account/i }));
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });
});
