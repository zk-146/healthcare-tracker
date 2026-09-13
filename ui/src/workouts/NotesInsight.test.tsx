import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import type { NotesInsightResponse } from '../api/types';
import { NotesInsight } from './NotesInsight';

function stubApi(get: ReturnType<typeof vi.fn>): ApiClient {
  return { get, post: vi.fn(), put: vi.fn(), del: vi.fn() } as unknown as ApiClient;
}

function insight(overrides: Partial<NotesInsightResponse> = {}): NotesInsightResponse {
  return {
    activityId: 'a1',
    available: true,
    mood: 'positive',
    painMentioned: false,
    painDescription: null,
    message: null,
    ...overrides,
  };
}

describe('NotesInsight', () => {
  it('shows only the analyze button and fetches nothing until clicked', () => {
    const get = vi.fn();
    render(<NotesInsight api={stubApi(get)} activityId="a1" />);

    expect(screen.getByRole('button', { name: 'Analyze notes' })).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });

  it('replaces the button with a skeleton while the analysis runs', async () => {
    const get = vi.fn().mockReturnValue(new Promise(() => {}));
    const { container } = render(<NotesInsight api={stubApi(get)} activityId="a1" />);

    await userEvent.click(screen.getByRole('button', { name: 'Analyze notes' }));

    expect(screen.queryByRole('button', { name: 'Analyze notes' })).not.toBeInTheDocument();
    expect(container.querySelector('.skeleton')).toBeInTheDocument();
  });

  it('shows the mood and that no pain was mentioned', async () => {
    const get = vi.fn().mockResolvedValue(insight());
    render(<NotesInsight api={stubApi(get)} activityId="a1" />);

    await userEvent.click(screen.getByRole('button', { name: 'Analyze notes' }));

    expect(get).toHaveBeenCalledWith('/api/v1/activities/a1/insights');
    expect(await screen.findByText('Mood: Positive')).toBeInTheDocument();
    expect(screen.getByText('No pain mentioned')).toBeInTheDocument();
  });

  it('shows the pain description when pain was mentioned', async () => {
    const get = vi
      .fn()
      .mockResolvedValue(
        insight({ mood: 'negative', painMentioned: true, painDescription: 'sore left knee' }),
      );
    render(<NotesInsight api={stubApi(get)} activityId="a1" />);

    await userEvent.click(screen.getByRole('button', { name: 'Analyze notes' }));

    expect(await screen.findByText('Mood: Negative')).toBeInTheDocument();
    expect(screen.getByText('Pain mentioned: sore left knee')).toBeInTheDocument();
  });

  it('says pain was mentioned even without a description', async () => {
    const get = vi.fn().mockResolvedValue(insight({ painMentioned: true, painDescription: null }));
    render(<NotesInsight api={stubApi(get)} activityId="a1" />);

    await userEvent.click(screen.getByRole('button', { name: 'Analyze notes' }));

    expect(await screen.findByText('Pain mentioned')).toBeInTheDocument();
  });

  it("shows the backend's message when the analysis is unavailable", async () => {
    const get = vi.fn().mockResolvedValue(
      insight({
        available: false,
        mood: null,
        painMentioned: null,
        message: 'AI analysis is unavailable right now.',
      }),
    );
    render(<NotesInsight api={stubApi(get)} activityId="a1" />);

    await userEvent.click(screen.getByRole('button', { name: 'Analyze notes' }));

    expect(await screen.findByText('AI analysis is unavailable right now.')).toBeInTheDocument();
    expect(screen.queryByText(/^Mood:/)).not.toBeInTheDocument();
  });

  it('shows an error note when the request fails', async () => {
    const get = vi.fn().mockRejectedValue(new ApiError(500, { error: 'Internal error' }));
    render(<NotesInsight api={stubApi(get)} activityId="a1" />);

    await userEvent.click(screen.getByRole('button', { name: 'Analyze notes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/internal error/i);
  });
});
