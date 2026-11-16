import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { ResetPasswordPage } from './ResetPasswordPage';

const TOKEN = 'token-from-the-email';

async function fill(password: string, confirmation: string) {
  await userEvent.type(screen.getByLabelText('New password'), password);
  await userEvent.type(screen.getByLabelText('Confirm new password'), confirmation);
  await userEvent.click(screen.getByRole('button', { name: /set new password/i }));
}

describe('ResetPasswordPage', () => {
  it('submits the token with the new password, then offers sign-in', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onDone = vi.fn();
    render(<ResetPasswordPage token={TOKEN} onSubmit={onSubmit} onDone={onDone} />);

    await fill('NewP@ssword456', 'NewP@ssword456');

    expect(onSubmit).toHaveBeenCalledWith(TOKEN, 'NewP@ssword456');
    expect(await screen.findByRole('status')).toHaveTextContent(/password has been changed/i);

    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(onDone).toHaveBeenCalled();
  });

  it('does not submit when the two passwords differ', async () => {
    const onSubmit = vi.fn();
    render(<ResetPasswordPage token={TOKEN} onSubmit={onSubmit} onDone={vi.fn()} />);

    await fill('NewP@ssword456', 'Different@789');

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match');
  });

  it("shows the server's password rule against the field", async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError(400, {
        status: 400,
        error: 'Validation failed',
        details: { newPassword: 'Password must be at least 12 characters' },
      }),
    );
    render(<ResetPasswordPage token={TOKEN} onSubmit={onSubmit} onDone={vi.fn()} />);

    await fill('weak', 'weak');

    expect(await screen.findByRole('alert')).toHaveTextContent('Password must be at least 12 characters');
    expect(screen.getByLabelText('New password')).toHaveAttribute('aria-invalid', 'true');
  });

  it('says so when the link is invalid or expired, and lets the user leave', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError(400, { status: 400, error: 'This link is invalid or has expired.' }),
    );
    const onDone = vi.fn();
    render(<ResetPasswordPage token={TOKEN} onSubmit={onSubmit} onDone={onDone} />);

    await fill('NewP@ssword456', 'NewP@ssword456');

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or has expired/i);

    await userEvent.click(screen.getByRole('button', { name: /back to sign in/i }));
    expect(onDone).toHaveBeenCalled();
  });
});
