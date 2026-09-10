import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import type { ProfileResponse } from '../api/types';
import { ProfilePage } from './ProfilePage';

const profile: ProfileResponse = {
  id: 'u1',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  dateOfBirth: '1990-01-01',
  gender: 'female',
  heightCm: 170,
  weightKg: 62,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
};

function stubApi(overrides: Partial<ApiClient> = {}): ApiClient {
  return {
    get: vi.fn().mockResolvedValue(profile),
    post: vi.fn(),
    put: vi.fn().mockResolvedValue(profile),
    del: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe('ProfilePage', () => {
  it('loads and displays the current profile', async () => {
    const api = stubApi();
    render(<ProfilePage api={api} onAccountDeleted={vi.fn()} />);

    expect(await screen.findByDisplayValue('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByText('ada@example.com')).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith('/api/v1/profile');
  });

  it('saves edited fields and shows a confirmation', async () => {
    const updated = { ...profile, fullName: 'Ada K. Lovelace' };
    const api = stubApi({ put: vi.fn().mockResolvedValue(updated) });
    render(<ProfilePage api={api} onAccountDeleted={vi.fn()} />);

    const fullName = await screen.findByLabelText(/full name/i);
    await userEvent.clear(fullName);
    await userEvent.type(fullName, 'Ada K. Lovelace');
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(api.put).toHaveBeenCalledWith(
        '/api/v1/profile',
        expect.objectContaining({ fullName: 'Ada K. Lovelace' }),
      );
    });
    expect(await screen.findByText('Saved.')).toBeInTheDocument();
  });

  it('rejects an empty full name before calling the API', async () => {
    const api = stubApi();
    render(<ProfilePage api={api} onAccountDeleted={vi.fn()} />);

    const fullName = await screen.findByLabelText(/full name/i);
    await userEvent.clear(fullName);
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    expect(await screen.findByText(/full name is required/i)).toBeInTheDocument();
    expect(api.put).not.toHaveBeenCalled();
  });

  it('shows a conflict banner on a concurrent-edit 409', async () => {
    const api = stubApi({ put: vi.fn().mockRejectedValue(new ApiError(409, { error: 'Conflict' })) });
    render(<ProfilePage api={api} onAccountDeleted={vi.fn()} />);

    await screen.findByDisplayValue('Ada Lovelace');
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    expect(await screen.findByText(/changed elsewhere/i)).toBeInTheDocument();
  });

  it('deletes the account after confirmation and notifies the parent', async () => {
    const onAccountDeleted = vi.fn();
    const api = stubApi();
    render(<ProfilePage api={api} onAccountDeleted={onAccountDeleted} />);

    await screen.findByDisplayValue('Ada Lovelace');
    await userEvent.click(screen.getByRole('button', { name: /delete my account/i }));
    await userEvent.click(screen.getByRole('button', { name: /confirm/i }));

    await waitFor(() => {
      expect(api.del).toHaveBeenCalledWith('/api/v1/profile');
    });
    expect(onAccountDeleted).toHaveBeenCalled();
  });

  it('does not delete when the confirmation is cancelled', async () => {
    const api = stubApi();
    render(<ProfilePage api={api} onAccountDeleted={vi.fn()} />);

    await screen.findByDisplayValue('Ada Lovelace');
    await userEvent.click(screen.getByRole('button', { name: /delete my account/i }));
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(api.del).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /delete my account/i })).toBeInTheDocument();
  });
});
