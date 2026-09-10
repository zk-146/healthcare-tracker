import { useCallback, useMemo, useState } from 'react';
import { createApiClient, type ApiClient } from '../api/client';
import {
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
} from '../api/endpoints';
import { localTokenStore } from './tokenStorage';

export interface AuthState {
  api: ApiClient;
  isAuthenticated: boolean;
  signIn(email: string, password: string): Promise<void>;
  signUp(email: string, password: string, fullName: string): Promise<void>;
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

  /** Registration returns a token pair just like login, so it signs the user in too. */
  const signUp = useCallback(async (email: string, password: string, fullName: string) => {
    const auth = await registerRequest(email, password, fullName);
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

  return { api, isAuthenticated, signIn, signUp, signOut };
}
