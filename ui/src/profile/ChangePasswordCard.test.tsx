import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import { ChangePasswordCard } from './ChangePasswordCard';

function stubApi(post: ReturnType<typeof vi.fn>): ApiClient {
  return { get: vi.fn(), post, put: vi.fn(), del: vi.fn(), postForm: vi.fn() } as unknown as ApiClient;
}

async function fill(fields: {
  current?: string;
  next?: string;
  confirm?: string;
} = {}) {
  const { current = 'OldPassw0rd!', next = 'NewPassw0rd!', confirm = 'NewPassw0rd!' } = fields;
  await userEvent.type(screen.getByLabelText(/current password/i), current);
  await userEvent.type(screen.getByLabelText(/^new password$/i), next);
  await userEvent.type(screen.getByLabelText(/confirm new password/i), confirm);
  await userEvent.click(screen.getByRole('button', { name: /^change password$/i }));
}

describe('ChangePasswordCard', () => {
  it('submits the current and new password, then notifies the parent', async () => {
    const post = vi.fn().mockResolvedValue(undefined);
    const onChanged = vi.fn();
    render(<ChangePasswordCard api={stubApi(post)} onChanged={onChanged} />);

    await fill();

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith('/api/v1/auth/change-password', {
        currentPassword: 'OldPassw0rd!',
        newPassword: 'NewPassw0rd!',
      });
    });
    expect(onChanged).toHaveBeenCalled();
  });

  it('blocks submission when the new password and confirmation do not match', async () => {
    const post = vi.fn();
    const onChanged = vi.fn();
    render(<ChangePasswordCard api={stubApi(post)} onChanged={onChanged} />);

    await fill({ confirm: 'something-else' });

    expect(post).not.toHaveBeenCalled();
    expect(onChanged).not.toHaveBeenCalled();
    expect(screen.getByText(/do not match/i)).toBeInTheDocument();
  });

  it('shows the backend message on an incorrect current password (401)', async () => {
    const post = vi.fn().mockRejectedValue(new ApiError(401, { error: 'Current password is incorrect' }));
    render(<ChangePasswordCard api={stubApi(post)} onChanged={vi.fn()} />);

    await fill();

    expect(await screen.findByText(/current password is incorrect/i)).toBeInTheDocument();
  });

  it('maps field-level validation errors from the server', async () => {
    const post = vi.fn().mockRejectedValue(
      new ApiError(400, {
        error: 'Validation failed',
        details: { newPassword: 'Password must be 12–128 characters and include a digit' },
      }),
    );
    render(<ChangePasswordCard api={stubApi(post)} onChanged={vi.fn()} />);

    await fill();

    expect(await screen.findByText(/must be 12–128 characters/i)).toBeInTheDocument();
  });
});
