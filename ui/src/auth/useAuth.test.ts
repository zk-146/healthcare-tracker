import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AuthResponse } from '../api/types';
import { localTokenStore } from './tokenStorage';
import { useAuth } from './useAuth';

const { login, register, logout } = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('../api/endpoints', () => ({ login, register, logout }));

const auth: AuthResponse = {
  token: 'access-token',
  refreshToken: 'refresh-token',
  expiresIn: 900,
  userId: 'u1',
  email: 'ada@example.com',
};

describe('useAuth', () => {
  beforeEach(() => {
    localTokenStore.clear();
    login.mockReset();
    register.mockReset();
    logout.mockReset();
  });

  it('starts unauthenticated with no stored tokens', () => {
    const { result } = renderHook(() => useAuth());
    expect(result.current.isAuthenticated).toBe(false);
  });

  it('signIn stores the returned tokens and flips isAuthenticated', async () => {
    login.mockResolvedValue(auth);
    const { result } = renderHook(() => useAuth());

    await act(() => result.current.signIn('ada@example.com', 'hunter2'));

    expect(login).toHaveBeenCalledWith('ada@example.com', 'hunter2');
    expect(localTokenStore.get()).toEqual({ token: auth.token, refreshToken: auth.refreshToken });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
  });

  it('signUp registers, stores tokens and signs the user in', async () => {
    register.mockResolvedValue(auth);
    const { result } = renderHook(() => useAuth());

    await act(() => result.current.signUp('ada@example.com', 'Sup3r-Secret!', 'Ada Lovelace'));

    expect(register).toHaveBeenCalledWith('ada@example.com', 'Sup3r-Secret!', 'Ada Lovelace');
    expect(localTokenStore.get()).toEqual({ token: auth.token, refreshToken: auth.refreshToken });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true));
  });

  it('signOut calls the logout endpoint and clears the session', async () => {
    logout.mockResolvedValue(undefined);
    localTokenStore.set({ token: 'a', refreshToken: 'b' });
    const { result } = renderHook(() => useAuth());
    expect(result.current.isAuthenticated).toBe(true);

    await act(() => result.current.signOut());

    expect(logout).toHaveBeenCalled();
    expect(localTokenStore.get()).toBeNull();
    await waitFor(() => expect(result.current.isAuthenticated).toBe(false));
  });

  it('clearSession drops the local session without calling logout', () => {
    localTokenStore.set({ token: 'a', refreshToken: 'b' });
    const { result } = renderHook(() => useAuth());
    expect(result.current.isAuthenticated).toBe(true);

    act(() => result.current.clearSession());

    expect(logout).not.toHaveBeenCalled();
    expect(localTokenStore.get()).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });
});
