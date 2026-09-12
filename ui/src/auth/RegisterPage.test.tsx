import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { RegisterPage } from './RegisterPage';

async function fillAndSubmit(
  overrides: Partial<{ fullName: string; email: string; password: string; confirmPassword: string }> = {},
) {
  const fields = {
    fullName: 'Ada Lovelace',
    email: 'ada@example.com',
    password: 'Sup3r-Secret!',
    confirmPassword: 'Sup3r-Secret!',
    ...overrides,
  };

  await userEvent.type(screen.getByLabelText(/full name/i), fields.fullName);
  await userEvent.type(screen.getByLabelText(/^email$/i), fields.email);
  await userEvent.type(screen.getByLabelText(/^password$/i), fields.password);
  await userEvent.type(screen.getByLabelText(/confirm password/i), fields.confirmPassword);
  await userEvent.click(screen.getByRole('button', { name: /create account/i }));

  return fields;
}

describe('RegisterPage', () => {
  it('submits the entered details', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<RegisterPage onSubmit={onSubmit} onSwitchToLogin={vi.fn()} />);

    const fields = await fillAndSubmit();

    expect(onSubmit).toHaveBeenCalledWith(fields.email, fields.password, fields.fullName);
  });

  it('blocks submission when the passwords do not match', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<RegisterPage onSubmit={onSubmit} onSwitchToLogin={vi.fn()} />);

    await fillAndSubmit({ confirmPassword: 'something-else' });

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent(/do not match/i);
  });

  it('maps field-level validation errors from the server', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError(400, {
        error: 'Validation failed',
        details: { password: 'Password must be 12–128 characters and include a special character' },
      }),
    );
    render(<RegisterPage onSubmit={onSubmit} onSwitchToLogin={vi.fn()} />);

    await fillAndSubmit();

    await waitFor(() => {
      expect(screen.getByText(/must be 12–128 characters/i)).toBeInTheDocument();
    });
  });

  it('shows a banner for a generic failure', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('Could not reach the server.'));
    render(<RegisterPage onSubmit={onSubmit} onSwitchToLogin={vi.fn()} />);

    await fillAndSubmit();

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/could not reach the server/i);
    });
  });

  it('lets the user switch back to the login form', async () => {
    const onSwitchToLogin = vi.fn();
    render(<RegisterPage onSubmit={vi.fn()} onSwitchToLogin={onSwitchToLogin} />);

    await userEvent.click(screen.getByRole('button', { name: /already have an account/i }));

    expect(onSwitchToLogin).toHaveBeenCalled();
  });
});
