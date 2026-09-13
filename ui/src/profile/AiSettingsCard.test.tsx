import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import { AiSettingsCard } from './AiSettingsCard';

function stubApi(overrides: Partial<ApiClient> = {}): ApiClient {
  return {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
    postForm: vi.fn(),
    ...overrides,
  } as ApiClient;
}

describe('AiSettingsCard', () => {
  const INACTIVE_NOTE = /this server uses its local ai model, so a deepseek key won't be used/i;

  it('warns that a key will not be used when the server is not on DeepSeek', () => {
    render(<AiSettingsCard api={stubApi()} status={{ connected: true, active: false }} onChanged={vi.fn()} />);

    expect(screen.getByText(INACTIVE_NOTE)).toBeInTheDocument();
  });

  it('shows no such warning when the server uses DeepSeek', () => {
    render(<AiSettingsCard api={stubApi()} status={{ connected: true, active: true }} onChanged={vi.fn()} />);

    expect(screen.queryByText(INACTIVE_NOTE)).not.toBeInTheDocument();
  });

  it('does not guess while the status is still loading', () => {
    render(<AiSettingsCard api={stubApi()} status={null} onChanged={vi.fn()} />);

    expect(screen.queryByText(INACTIVE_NOTE)).not.toBeInTheDocument();
  });
  it('saves a new key against the dedicated endpoint and notifies the parent', async () => {
    const put = vi.fn().mockResolvedValue({ connected: true });
    const onChanged = vi.fn();
    render(<AiSettingsCard api={stubApi({ put })} status={{ connected: false, active: true }} onChanged={onChanged} />);

    await userEvent.type(screen.getByLabelText(/deepseek api key/i), 'sk-my-real-key');
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => {
      expect(put).toHaveBeenCalledWith('/api/v1/integrations/deepseek', { apiKey: 'sk-my-real-key' });
    });
    expect(onChanged).toHaveBeenCalled();
    expect(await screen.findByText(/^saved\.$/i)).toBeInTheDocument();
  });

  it('clears the field after a successful save, never re-displaying the key', async () => {
    const put = vi.fn().mockResolvedValue({ connected: true });
    render(<AiSettingsCard api={stubApi({ put })} status={{ connected: false, active: true }} onChanged={vi.fn()} />);

    const input = screen.getByLabelText(/deepseek api key/i) as HTMLInputElement;
    await userEvent.type(input, 'sk-my-real-key');
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(input.value).toBe(''));
  });

  it('disables Save and never calls the API when the field is blank', async () => {
    const put = vi.fn();
    render(<AiSettingsCard api={stubApi({ put })} status={{ connected: false, active: true }} onChanged={vi.fn()} />);

    expect(screen.getByRole('button', { name: /^save$/i })).toBeDisabled();
    expect(put).not.toHaveBeenCalled();
  });

  it('offers a distinct Remove-key action only when a key is already configured', () => {
    render(<AiSettingsCard api={stubApi()} status={{ connected: false, active: true }} onChanged={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /remove key/i })).not.toBeInTheDocument();
  });

  it('treats a null status (still loading) the same as not configured', () => {
    render(<AiSettingsCard api={stubApi()} status={null} onChanged={vi.fn()} />);
    expect(screen.queryByRole('button', { name: /remove key/i })).not.toBeInTheDocument();
    expect(screen.getByText(/add your own deepseek api key/i)).toBeInTheDocument();
  });

  it('removing the key calls the dedicated disconnect endpoint with no field required', async () => {
    const del = vi.fn().mockResolvedValue(undefined);
    const onChanged = vi.fn();
    render(<AiSettingsCard api={stubApi({ del })} status={{ connected: true, active: true }} onChanged={onChanged} />);

    await userEvent.click(screen.getByRole('button', { name: /remove key/i }));

    await waitFor(() => {
      expect(del).toHaveBeenCalledWith('/api/v1/integrations/deepseek');
    });
    expect(onChanged).toHaveBeenCalled();
    expect(await screen.findByText(/key removed/i)).toBeInTheDocument();
  });

  it('shows the friendly conflict message on a 409, matching ProfileForm', async () => {
    const put = vi.fn().mockRejectedValue(new ApiError(409, { error: 'Conflict' }));
    render(<AiSettingsCard api={stubApi({ put })} status={{ connected: false, active: true }} onChanged={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/deepseek api key/i), 'sk-my-real-key');
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }));

    expect(await screen.findByText(/changed elsewhere/i)).toBeInTheDocument();
  });

  it('maps a field-level validation error onto the input, per the splitDetails convention', async () => {
    const put = vi.fn().mockRejectedValue(
      new ApiError(400, { error: 'Validation failed', details: { apiKey: 'API key is too long' } }),
    );
    render(<AiSettingsCard api={stubApi({ put })} status={{ connected: false, active: true }} onChanged={vi.fn()} />);

    const input = screen.getByLabelText(/deepseek api key/i) as HTMLInputElement;
    await userEvent.type(input, 'sk-way-too-long');
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }));

    expect(await screen.findByText(/api key is too long/i)).toBeInTheDocument();
    expect(input.value).toBe('sk-way-too-long');
  });

  it('does not confuse a save failure banner with a stale success message', async () => {
    const put = vi.fn().mockRejectedValue(new ApiError(500, { error: 'Server error' }));
    render(<AiSettingsCard api={stubApi({ put })} status={{ connected: false, active: true }} onChanged={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/deepseek api key/i), 'sk-my-real-key');
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await screen.findByText(/server error/i);
    expect(screen.queryByText(/^saved\.$/i)).not.toBeInTheDocument();
  });
});
