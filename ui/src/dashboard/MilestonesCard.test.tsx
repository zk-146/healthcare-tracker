import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError, type ApiClient } from '../api/client';
import type { MilestoneResponse } from '../api/types';
import { MilestonesCard } from './MilestonesCard';

function stubApi(get: ReturnType<typeof vi.fn>): ApiClient {
  return { get, post: vi.fn(), put: vi.fn(), del: vi.fn(), postForm: vi.fn() } as unknown as ApiClient;
}

function badge(threshold: number): HTMLElement {
  return screen.getByText(String(threshold)).closest('li') as HTMLElement;
}

describe('MilestonesCard', () => {
  it('renders the full ladder, marking earned thresholds and their dates', async () => {
    const milestones: MilestoneResponse[] = [
      { milestoneDays: 7, achievedAt: '2026-08-20T09:00:00' },
      { milestoneDays: 3, achievedAt: '2026-08-15T09:00:00' },
    ];
    render(<MilestonesCard api={stubApi(vi.fn().mockResolvedValue(milestones))} />);

    expect(await screen.findByText('365')).toBeInTheDocument();

    expect(badge(3)).toHaveAttribute('data-earned', 'true');
    expect(within(badge(3)).getByText(/15 Aug/)).toBeInTheDocument();
    expect(badge(7)).toHaveAttribute('data-earned', 'true');
    expect(within(badge(7)).getByText(/20 Aug/)).toBeInTheDocument();

    expect(badge(14)).toHaveAttribute('data-earned', 'false');
    expect(within(badge(14)).getByText('Not yet')).toBeInTheDocument();
    expect(badge(365)).toHaveAttribute('data-earned', 'false');
  });

  it('renders every threshold unearned for a user with no milestones yet', async () => {
    render(<MilestonesCard api={stubApi(vi.fn().mockResolvedValue([]))} />);

    expect(await screen.findByText('3')).toBeInTheDocument();
    for (const threshold of [3, 7, 14, 30, 60, 100, 365]) {
      expect(badge(threshold)).toHaveAttribute('data-earned', 'false');
    }
  });

  it('shows an error note when the request fails', async () => {
    const get = vi.fn().mockRejectedValue(new ApiError(500, { error: 'Internal error' }));
    render(<MilestonesCard api={stubApi(get)} />);

    expect(await screen.findByText(/internal error/i)).toBeInTheDocument();
  });
});
