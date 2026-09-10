import type { ApiErrorBody, AuthResponse } from './types';

export interface Tokens {
  token: string;
  refreshToken: string;
}

export interface TokenStore {
  get(): Tokens | null;
  set(tokens: Tokens): void;
  clear(): void;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody | null,
  ) {
    super(body?.error ?? `HTTP ${status}`);
    this.name = 'ApiError';
  }
}

export interface ApiClient {
  get<T>(path: string): Promise<T>;
  post<T>(path: string, body?: unknown): Promise<T>;
  put<T>(path: string, body?: unknown): Promise<T>;
  /** Named `del` because `delete` is a reserved word. */
  del<T>(path: string): Promise<T>;
  /**
   * Multipart upload (file imports). Distinct from `post` because a FormData body must
   * NOT get a hand-set Content-Type: the browser derives one with the multipart
   * boundary, and overriding it (as `post`'s JSON path does) breaks the upload.
   */
  postForm<T>(path: string, form: FormData): Promise<T>;
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody;
  } catch {
    return null;
  }
}

/** Shared by every call site per the plan's global constraint (see below). */
function userTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

/**
 * The only module that knows tokens exist.
 *
 * Refresh is single-flight: the dashboard fires three calls in parallel on mount, so an
 * expired access token produces three simultaneous 401s. Without the shared promise they
 * would trigger three refreshes racing to overwrite each other's tokens.
 */
export function createApiClient(
  store: TokenStore,
  onAuthFailure: () => void,
  fetchImpl: typeof fetch = fetch,
): ApiClient {
  let refreshInFlight: Promise<Tokens> | null = null;

  function send(path: string, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set('X-User-Timezone', userTimeZone());
    const tokens = store.get();
    if (tokens !== null) {
      headers.set('Authorization', `Bearer ${tokens.token}`);
    }
    if (init.body !== undefined && !(init.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
    }
    return fetchImpl(path, { ...init, headers });
  }

  function refresh(): Promise<Tokens> {
    if (refreshInFlight === null) {
      refreshInFlight = (async () => {
        const tokens = store.get();
        if (tokens === null) {
          throw new ApiError(401, null);
        }
        // Global constraint: every API call sends X-User-Timezone. Do not simplify
        // this back to a bare Content-Type header.
        const response = await fetchImpl('/api/v1/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-User-Timezone': userTimeZone() },
          body: JSON.stringify({ refreshToken: tokens.refreshToken }),
        });
        if (!response.ok) {
          throw new ApiError(response.status, await readErrorBody(response));
        }
        const auth = (await response.json()) as AuthResponse;
        const next: Tokens = { token: auth.token, refreshToken: auth.refreshToken };
        store.set(next);
        return next;
      })()
        // This .catch runs exactly once per failed refresh attempt, no matter how many
        // concurrent callers are awaiting the shared `refreshInFlight` promise below — a
        // rejected promise re-throws independently at each `await` site, but the handler
        // attached to the promise itself only executes once. Resetting refreshInFlight in
        // .finally (not here) means a later, separate refresh failure creates a fresh
        // promise with its own .catch, so onAuthFailure is not permanently latched.
        .catch((err: unknown) => {
          store.clear();
          onAuthFailure();
          throw err;
        })
        .finally(() => {
          refreshInFlight = null;
        });
    }
    return refreshInFlight;
  }

  async function request<T>(path: string, init: RequestInit): Promise<T> {
    let response = await send(path, init);

    if (response.status === 401) {
      try {
        await refresh();
      } catch {
        throw new ApiError(401, null);
      }
      response = await send(path, init);
      if (response.status === 401) {
        store.clear();
        onAuthFailure();
        throw new ApiError(401, null);
      }
    }

    if (!response.ok) {
      throw new ApiError(response.status, await readErrorBody(response));
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  return {
    get<T>(path: string) {
      return request<T>(path, { method: 'GET' });
    },
    post<T>(path: string, body?: unknown) {
      return request<T>(path, {
        method: 'POST',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    },
    put<T>(path: string, body?: unknown) {
      return request<T>(path, {
        method: 'PUT',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    },
    del<T>(path: string) {
      return request<T>(path, { method: 'DELETE' });
    },
    postForm<T>(path: string, form: FormData) {
      return request<T>(path, { method: 'POST', body: form });
    },
  };
}
