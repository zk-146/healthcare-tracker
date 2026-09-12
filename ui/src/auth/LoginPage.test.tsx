import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';

describe('LoginPage', () => {
  it('submits the entered credentials', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<LoginPage onSubmit={onSubmit} onSwitchToRegister={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/email/i), 'z@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'hunter2');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(onSubmit).toHaveBeenCalledWith('z@example.com', 'hunter2');
  });

  it('shows an error message when sign-in is rejected', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error('Invalid credentials'));
    render(<LoginPage onSubmit={onSubmit} onSwitchToRegister={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/email/i), 'z@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/invalid credentials/i);
    });
  });

  it('disables the button while the request is in flight', async () => {
    let resolve: () => void = () => {};
    const onSubmit = vi.fn().mockReturnValue(new Promise<void>((r) => { resolve = r; }));
    render(<LoginPage onSubmit={onSubmit} onSwitchToRegister={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/email/i), 'z@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'hunter2');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(screen.getByRole('button', { name: /signing in/i })).toBeDisabled();
    resolve();
  });

  it('lets the user switch to the registration form', async () => {
    const onSwitchToRegister = vi.fn();
    render(<LoginPage onSubmit={vi.fn()} onSwitchToRegister={onSwitchToRegister} />);

    await userEvent.click(screen.getByRole('button', { name: /create an account/i }));

    expect(onSwitchToRegister).toHaveBeenCalled();
  });
});
