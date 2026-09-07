import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useLoadable } from './useLoadable';

describe('useLoadable', () => {
  it('starts loading and resolves to ready', async () => {
    const { result } = renderHook(() => useLoadable(() => Promise.resolve(42), []));

    expect(result.current.state).toBe('loading');
    await waitFor(() => expect(result.current).toEqual({ state: 'ready', value: 42 }));
  });

  it('resolves to error with a friendly message', async () => {
    const { result } = renderHook(() => useLoadable(() => Promise.reject(new Error('boom')), []));

    await waitFor(() => expect(result.current).toEqual({ state: 'error', message: 'boom' }));
  });

  it('ignores a resolution that lands after unmount', async () => {
    const load = vi.fn(() => Promise.resolve('late'));
    const { unmount } = renderHook(() => useLoadable(load, []));
    unmount();

    await Promise.resolve();
    expect(load).toHaveBeenCalledTimes(1);
  });
});
