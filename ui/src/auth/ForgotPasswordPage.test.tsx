import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { ForgotPasswordPage } from './ForgotPasswordPage';

describe('ForgotPasswordPage', () => {
  it('submits the address and then describes the outcome without confirming an account exists', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<ForgotPasswordPage onSubmit={onSubmit} onBackToLogin={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/email/i), 'z@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(onSubmit).toHaveBeenCalledWith('z@example.com');
    expect(await screen.findByRole('status')).toHaveTextContent(
      /if an account exists for z@example\.com/i,
    );
  });

  it('shows an error and stays on the form when the request fails', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(429, null));
    render(<ForgotPasswordPage onSubmit={onSubmit} onBackToLogin={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/email/i), 'z@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/too many requests/i);
    expect(screen.getByRole('button', { name: /send reset link/i })).toBeEnabled();
  });

  it('returns to sign-in from the form and from the confirmation', async () => {
    const onBackToLogin = vi.fn();
    render(
      <ForgotPasswordPage onSubmit={vi.fn().mockResolvedValue(undefined)} onBackToLogin={onBackToLogin} />,
    );

    await userEvent.click(screen.getByRole('button', { name: /back to sign in/i }));
    expect(onBackToLogin).toHaveBeenCalledTimes(1);

    await userEvent.type(screen.getByLabelText(/email/i), 'z@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));
    await userEvent.click(await screen.findByRole('button', { name: /back to sign in/i }));
    expect(onBackToLogin).toHaveBeenCalledTimes(2);
  });
});
