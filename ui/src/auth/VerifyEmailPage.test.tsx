import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { VerifyEmailPage } from './VerifyEmailPage';

const TOKEN = 'token-from-the-email';

describe('VerifyEmailPage', () => {
  it('does not spend the token until the user confirms', () => {
    const onSubmit = vi.fn();
    render(<VerifyEmailPage token={TOKEN} onSubmit={onSubmit} onDone={vi.fn()} />);

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('confirms on request, then continues', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const onDone = vi.fn();
    render(<VerifyEmailPage token={TOKEN} onSubmit={onSubmit} onDone={onDone} />);

    await userEvent.click(screen.getByRole('button', { name: /confirm email address/i }));

    expect(onSubmit).toHaveBeenCalledWith(TOKEN);
    expect(await screen.findByRole('status')).toHaveTextContent(/is confirmed/i);

    await userEvent.click(screen.getByRole('button', { name: /continue/i }));
    expect(onDone).toHaveBeenCalled();
  });

  it('says so when the link is invalid or expired', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError(400, { status: 400, error: 'This link is invalid or has expired.' }),
    );
    render(<VerifyEmailPage token={TOKEN} onSubmit={onSubmit} onDone={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: /confirm email address/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or has expired/i);
  });

  it('can be dismissed without confirming', async () => {
    const onSubmit = vi.fn();
    const onDone = vi.fn();
    render(<VerifyEmailPage token={TOKEN} onSubmit={onSubmit} onDone={onDone} />);

    await userEvent.click(screen.getByRole('button', { name: /not now/i }));

    expect(onDone).toHaveBeenCalled();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
