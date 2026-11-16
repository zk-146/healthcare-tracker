import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import { EmailVerificationNote } from './EmailVerificationNote';

function stubApi(post: ApiClient['post']): ApiClient {
  return { get: vi.fn(), post, put: vi.fn(), del: vi.fn(), postForm: vi.fn() };
}

describe('EmailVerificationNote', () => {
  it('says the address is confirmed and offers nothing to send', () => {
    render(<EmailVerificationNote api={stubApi(vi.fn())} verified />);

    expect(screen.getByText('Email confirmed')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('sends a confirmation email on request and says so', async () => {
    const post = vi.fn().mockResolvedValue(undefined);
    render(<EmailVerificationNote api={stubApi(post)} verified={false} />);

    await userEvent.click(screen.getByRole('button', { name: /send confirmation email/i }));

    expect(post).toHaveBeenCalledWith('/api/v1/auth/resend-verification');
    expect(await screen.findByRole('status')).toHaveTextContent(/confirmation email sent/i);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('shows the error and keeps the button when sending fails', async () => {
    const post = vi.fn().mockRejectedValue(new ApiError(429, null));
    render(<EmailVerificationNote api={stubApi(post)} verified={false} />);

    await userEvent.click(screen.getByRole('button', { name: /send confirmation email/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/too many requests/i);
    expect(screen.getByRole('button', { name: /send confirmation email/i })).toBeEnabled();
  });
});
