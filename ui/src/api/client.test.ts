import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient, type TokenStore, type Tokens } from './client';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function makeStore(initial: Tokens | null): TokenStore & { current: Tokens | null } {
  return {
    current: initial,
    get() {
      return this.current;
    },
    set(t: Tokens) {
      this.current = t;
    },
    clear() {
      this.current = null;
    },
  };
}

describe('createApiClient', () => {
  let store: ReturnType<typeof makeStore>;
  // Parameterized because vitest 4 tightened vi.fn()'s default type: an unparameterized
  // ReturnType<typeof vi.fn> resolves to Mock<Procedure | Constructable>, which is no
  // longer assignable to createApiClient's `onAuthFailure: () => void`. Do not simplify
  // this back to the bare form.
  let onAuthFailure: ReturnType<typeof vi.fn<() => void>>;

  beforeEach(() => {
    store = makeStore({ token: 'access-1', refreshToken: 'refresh-1' });
    onAuthFailure = vi.fn<() => void>();
  });

  it('sends the bearer token and the browser timezone', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    await api.get('/api/v1/summary/daily');

    const [, init] = fetchMock.mock.calls[0];
    const headers = init.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer access-1');
    expect(headers.get('X-User-Timezone')).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    );
  });

  it('fires exactly one refresh for three concurrent 401s and retries each call', async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url === '/api/v1/auth/refresh') {
        return jsonResponse({
          token: 'access-2',
          refreshToken: 'refresh-2',
          expiresIn: 3600,
          userId: 'u1',
          email: 'z@example.com',
        });
      }
      const auth = (init?.headers as Headers).get('Authorization');
      if (auth === 'Bearer access-1') {
        return jsonResponse({ status: 401, error: 'Unauthorized' }, 401);
      }
      return jsonResponse({ path: url });
    });

    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    const results = await Promise.all([
      api.get<{ path: string }>('/api/v1/a'),
      api.get<{ path: string }>('/api/v1/b'),
      api.get<{ path: string }>('/api/v1/c'),
    ]);

    const refreshCalls = fetchMock.mock.calls.filter(([url]) => url === '/api/v1/auth/refresh');
    expect(refreshCalls).toHaveLength(1);
    expect(results.map((r) => r.path).sort()).toEqual(['/api/v1/a', '/api/v1/b', '/api/v1/c']);
    expect(store.current?.token).toBe('access-2');
    expect(onAuthFailure).not.toHaveBeenCalled();
  });

  it('clears tokens and signals auth failure when refresh is rejected', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/v1/auth/refresh') {
        return jsonResponse({ status: 401, error: 'Unauthorized' }, 401);
      }
      return jsonResponse({ status: 401, error: 'Unauthorized' }, 401);
    });

    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    await expect(api.get('/api/v1/summary/daily')).rejects.toBeInstanceOf(ApiError);
    expect(store.current).toBeNull();
    expect(onAuthFailure).toHaveBeenCalledTimes(1);
  });

  it('surfaces the backend error body on a non-401 failure', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ error: 'Validation failed', timestamp: 'now' }, 400));
    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    await expect(api.get('/api/v1/summary')).rejects.toMatchObject({
      status: 400,
      body: { error: 'Validation failed' },
    });
  });

  it('does not retry a 429', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ error: 'Too many requests' }, 429));
    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    await expect(api.get('/api/v1/summary/daily')).rejects.toMatchObject({ status: 429 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
