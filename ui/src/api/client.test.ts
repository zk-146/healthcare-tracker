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

  // Regression test for a real bug: change-password returns 401 for "current password
  // is incorrect" (a business-logic error, not an expired token). The default 401
  // handling would refresh the (still-valid) token, retry, get the same 401 again, and
  // force-sign the user out -- discarding the backend's real error message. retryOn401:
  // false must skip refresh/retry entirely and surface the body straight through.
  it('does not refresh, retry, or sign out on a 401 when retryOn401 is false', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ status: 401, error: 'Current password is incorrect' }, 401));
    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    await expect(
      api.post('/api/v1/auth/change-password', { currentPassword: 'wrong' }, { retryOn401: false }),
    ).rejects.toMatchObject({ status: 401, body: { error: 'Current password is incorrect' } });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(onAuthFailure).not.toHaveBeenCalled();
    expect(store.current).not.toBeNull();
  });

  it('does not retry a 429', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ error: 'Too many requests' }, 429));
    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    await expect(api.get('/api/v1/summary/daily')).rejects.toMatchObject({ status: 429 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('signals auth failure exactly once when three concurrent 401s share a rejected refresh', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/v1/auth/refresh') {
        return jsonResponse({ status: 401, error: 'Unauthorized' }, 401);
      }
      return jsonResponse({ status: 401, error: 'Unauthorized' }, 401);
    });

    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    const results = await Promise.allSettled([
      api.get('/api/v1/a'),
      api.get('/api/v1/b'),
      api.get('/api/v1/c'),
    ]);

    for (const result of results) {
      expect(result.status).toBe('rejected');
      if (result.status === 'rejected') {
        expect(result.reason).toBeInstanceOf(ApiError);
      }
    }
    expect(onAuthFailure).toHaveBeenCalledTimes(1);
    expect(store.current).toBeNull();
  });

  it('clears tokens and signals auth failure when the retried request is still 401 after a successful refresh', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/v1/auth/refresh') {
        return jsonResponse({
          token: 'access-2',
          refreshToken: 'refresh-2',
          expiresIn: 3600,
          userId: 'u1',
          email: 'z@example.com',
        });
      }
      // Every call to the real endpoint keeps returning 401, even after a successful refresh.
      return jsonResponse({ status: 401, error: 'Unauthorized' }, 401);
    });

    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    await expect(api.get('/api/v1/summary/daily')).rejects.toBeInstanceOf(ApiError);
    expect(store.current).toBeNull();
    expect(onAuthFailure).toHaveBeenCalledTimes(1);
  });

  it('sends a PUT with a JSON body and returns the parsed response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'a1' }));
    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    const result = await api.put('/api/v1/activities/a1', { durationMinutes: 30 });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/activities/a1');
    expect(init.method).toBe('PUT');
    expect(init.body).toBe(JSON.stringify({ durationMinutes: 30 }));
    expect((init.headers as Headers).get('Content-Type')).toBe('application/json');
    expect(result).toEqual({ id: 'a1' });
  });

  it('sends a DELETE with no body and resolves to undefined on 204', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    const result = await api.del('/api/v1/activities/a1');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/activities/a1');
    expect(init.method).toBe('DELETE');
    expect(init.body).toBeUndefined();
    expect(result).toBeUndefined();
  });

  it('throws an ApiError carrying the parsed body when a PUT conflicts', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ status: 409, error: 'Activity was modified' }, 409));
    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);

    await expect(api.put('/api/v1/activities/a1', {})).rejects.toMatchObject({
      status: 409,
      body: { error: 'Activity was modified' },
    });
  });

  it('sends a multipart POST without hand-setting Content-Type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ imported: 3 }));
    const api = createApiClient(store, onAuthFailure, fetchMock as unknown as typeof fetch);
    const form = new FormData();
    form.append('file', new File(['a,b\n1,2'], 'export.csv', { type: 'text/csv' }));

    const result = await api.postForm('/api/v1/activities/import/fitbit', form);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/activities/import/fitbit');
    expect(init.method).toBe('POST');
    expect(init.body).toBe(form);
    // Left unset deliberately: the browser derives Content-Type (with the multipart
    // boundary) from the FormData body itself; a hand-set value would break the upload.
    expect((init.headers as Headers).get('Content-Type')).toBeNull();
    expect((init.headers as Headers).get('Authorization')).toBe('Bearer access-1');
    expect(result).toEqual({ imported: 3 });
  });
});
