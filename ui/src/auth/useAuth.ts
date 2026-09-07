import { useCallback, useMemo, useState } from 'react';
import { createApiClient, type ApiClient } from '../api/client';
import { login as loginRequest, logout as logoutRequest } from '../api/endpoints';
import { localTokenStore } from './tokenStorage';

export interface AuthState {
  api: ApiClient;
  isAuthenticated: boolean;
  signIn(email: string, password: string): Promise<void>;
  signOut(): Promise<void>;
}

export function useAuth(): AuthState {
  const [isAuthenticated, setAuthenticated] = useState(() => localTokenStore.get() !== null);

  const api = useMemo(
    () =>
      createApiClient(localTokenStore, () => {
        setAuthenticated(false);
      }),
    [],
  );

  const signIn = useCallback(async (email: string, password: string) => {
    const auth = await loginRequest(email, password);
    localTokenStore.set({ token: auth.token, refreshToken: auth.refreshToken });
    setAuthenticated(true);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await logoutRequest(api);
    } finally {
      localTokenStore.clear();
      setAuthenticated(false);
    }
  }, [api]);

  return { api, isAuthenticated, signIn, signOut };
}
