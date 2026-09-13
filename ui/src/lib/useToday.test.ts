import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useToday } from './useToday';

describe('useToday', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('starts on the current local day', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 13, 15, 0));

    const { result } = renderHook(() => useToday());

    expect(result.current).toBe('2026-09-13');
  });

  it('rolls over at local midnight with nothing else re-rendering the page', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 13, 23, 59, 30));
    const { result } = renderHook(() => useToday());

    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    expect(result.current).toBe('2026-09-14');
  });

  it('keeps rolling over on the days after', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 13, 23, 59, 30));
    const { result } = renderHook(() => useToday());

    act(() => {
      vi.advanceTimersByTime(60_000);
    });
    act(() => {
      vi.advanceTimersByTime(24 * 60 * 60 * 1000);
    });

    expect(result.current).toBe('2026-09-15');
  });

  it('cancels its timer on unmount', () => {
    vi.useFakeTimers();
    const { unmount } = renderHook(() => useToday());

    unmount();

    expect(vi.getTimerCount()).toBe(0);
  });
});
