import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from './client';
import {
  createActivity,
  deleteAccount,
  deleteActivity,
  disconnectGoogleHealth,
  getGoogleHealthConnectUrl,
  getProfile,
  listAllActivities,
  login,
  register,
  updateActivity,
  updateProfile,
} from './endpoints';
import type { ActivityInput, ActivityResponse, AuthResponse, ProfileResponse } from './types';

function spyClient(): ApiClient {
  return {
    get: vi.fn().mockResolvedValue({}),
    post: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
    del: vi.fn().mockResolvedValue(undefined),
  } as unknown as ApiClient;
}

const minimal: ActivityInput = {
  activityType: 'RUNNING',
  startedAt: '2026-09-08T07:30:00',
  durationMinutes: 45,
};

const originalManual: ActivityResponse = {
  id: 'a1',
  activityType: 'RUNNING',
  source: 'MANUAL',
  deviceId: null,
  startedAt: '2026-09-08T07:30:00',
  endedAt: null,
  durationMinutes: 45,
  distanceKm: null,
  caloriesBurned: null,
  heartRateAvg: null,
  steps: null,
  notes: null,
  createdAt: '2026-09-08T07:00:00',
  updatedAt: '2026-09-08T07:00:00',
};

describe('activity write endpoints', () => {
  it('posts a create with source MANUAL and no endedAt', async () => {
    const api = spyClient();

    await createActivity(api, minimal);

    expect(api.post).toHaveBeenCalledWith('/api/v1/activities', {
      activityType: 'RUNNING',
      source: 'MANUAL',
      startedAt: '2026-09-08T07:30:00',
      durationMinutes: 45,
    });
  });

  it('includes only the optional fields that were supplied', async () => {
    const api = spyClient();

    await createActivity(api, { ...minimal, distanceKm: 8.2, notes: 'river loop' });

    expect(api.post).toHaveBeenCalledWith('/api/v1/activities', {
      activityType: 'RUNNING',
      source: 'MANUAL',
      startedAt: '2026-09-08T07:30:00',
      durationMinutes: 45,
      distanceKm: 8.2,
      notes: 'river loop',
    });
  });

  it('puts an update to the activity id, with the same body shape', async () => {
    const api = spyClient();

    await updateActivity(api, 'a1', { ...minimal, steps: 9000 }, originalManual);

    expect(api.put).toHaveBeenCalledWith('/api/v1/activities/a1', {
      activityType: 'RUNNING',
      source: 'MANUAL',
      startedAt: '2026-09-08T07:30:00',
      durationMinutes: 45,
      steps: 9000,
    });
  });

  it('carries the original source and deviceId through on an update, not MANUAL', async () => {
    const api = spyClient();
    const original: ActivityResponse = {
      ...originalManual,
      source: 'CSV_IMPORT',
      deviceId: 'device-123',
    };

    await updateActivity(api, 'a1', minimal, original);

    expect(api.put).toHaveBeenCalledWith('/api/v1/activities/a1', {
      activityType: 'RUNNING',
      source: 'CSV_IMPORT',
      startedAt: '2026-09-08T07:30:00',
      durationMinutes: 45,
      deviceId: 'device-123',
    });
  });

  it('echoes the original endedAt when still consistent with the submitted times', async () => {
    const api = spyClient();
    const original: ActivityResponse = {
      ...originalManual,
      source: 'IOT',
      deviceId: 'watch-1',
      endedAt: '2026-09-08T08:15:00',
    };

    await updateActivity(api, 'a1', minimal, original);

    expect(api.put).toHaveBeenCalledWith('/api/v1/activities/a1', {
      activityType: 'RUNNING',
      source: 'IOT',
      startedAt: '2026-09-08T07:30:00',
      durationMinutes: 45,
      deviceId: 'watch-1',
      endedAt: '2026-09-08T08:15:00',
    });
  });

  it('omits endedAt when the edited duration no longer agrees with it', async () => {
    const api = spyClient();
    const original: ActivityResponse = {
      ...originalManual,
      source: 'IOT',
      deviceId: 'watch-1',
      endedAt: '2026-09-08T08:15:00',
    };

    await updateActivity(api, 'a1', { ...minimal, durationMinutes: 90 }, original);

    expect(api.put).toHaveBeenCalledWith('/api/v1/activities/a1', {
      activityType: 'RUNNING',
      source: 'IOT',
      startedAt: '2026-09-08T07:30:00',
      durationMinutes: 90,
      deviceId: 'watch-1',
    });
  });

  it('omits deviceId when the original activity has none', async () => {
    const api = spyClient();

    await updateActivity(api, 'a1', minimal, originalManual);

    const [, body] = (api.put as ReturnType<typeof vi.fn>).mock.calls[0] as [string, Record<string, unknown>];
    expect(body).not.toHaveProperty('deviceId');
  });

  it('deletes by id', async () => {
    const api = spyClient();

    await deleteActivity(api, 'a1');

    expect(api.del).toHaveBeenCalledWith('/api/v1/activities/a1');
  });

  it('requests full history with no date filter, newest first', async () => {
    const api = spyClient();

    await listAllActivities(api, 2);

    expect(api.get).toHaveBeenCalledWith(
      '/api/v1/activities?page=2&size=20&sort=startedAt%2Cdesc',
    );
  });
});

const auth: AuthResponse = {
  token: 'access-token',
  refreshToken: 'refresh-token',
  expiresIn: 900,
  userId: 'u1',
  email: 'ada@example.com',
};

function fetchStub(status: number, body: unknown) {
  return vi.fn().mockResolvedValue({
    ok: status < 400,
    status,
    json: () => Promise.resolve(body),
  });
}

describe('unauthenticated auth endpoints', () => {
  it('posts login credentials with the timezone header, no bearer token', async () => {
    const fetchImpl = fetchStub(200, auth);

    const result = await login('ada@example.com', 'hunter2', fetchImpl as unknown as typeof fetch);

    expect(result).toEqual(auth);
    const [path, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(path).toBe('/api/v1/auth/login');
    expect(JSON.parse(init.body as string)).toEqual({ email: 'ada@example.com', password: 'hunter2' });
    const headers = init.headers as Record<string, string>;
    expect(headers['X-User-Timezone']).toBeTruthy();
    expect(headers.Authorization).toBeUndefined();
  });

  it('posts registration details and returns the token pair', async () => {
    const fetchImpl = fetchStub(201, auth);

    const result = await register(
      'ada@example.com',
      'Sup3r-Secret!',
      'Ada Lovelace',
      fetchImpl as unknown as typeof fetch,
    );

    expect(result).toEqual(auth);
    const [path, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(path).toBe('/api/v1/auth/register');
    expect(JSON.parse(init.body as string)).toEqual({
      email: 'ada@example.com',
      password: 'Sup3r-Secret!',
      fullName: 'Ada Lovelace',
    });
  });

  it('throws an ApiError carrying the response body on failure', async () => {
    const details = { email: 'Email already registered' };
    const fetchImpl = fetchStub(409, { error: 'Conflict', details });

    await expect(
      register('ada@example.com', 'Sup3r-Secret!', 'Ada Lovelace', fetchImpl as unknown as typeof fetch),
    ).rejects.toMatchObject({ status: 409, body: { error: 'Conflict', details } });
  });
});

const profile: ProfileResponse = {
  id: 'u1',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  dateOfBirth: '1990-01-01',
  gender: 'female',
  heightCm: 170,
  weightKg: 62,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
};

describe('profile endpoints', () => {
  it('fetches the current profile', async () => {
    const api = spyClient();
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue(profile);

    const result = await getProfile(api);

    expect(api.get).toHaveBeenCalledWith('/api/v1/profile');
    expect(result).toEqual(profile);
  });

  it('puts a partial update as given, without inventing fields', async () => {
    const api = spyClient();

    await updateProfile(api, { fullName: 'Ada K. Lovelace' });

    expect(api.put).toHaveBeenCalledWith('/api/v1/profile', { fullName: 'Ada K. Lovelace' });
  });

  it('deletes the account', async () => {
    const api = spyClient();

    await deleteAccount(api);

    expect(api.del).toHaveBeenCalledWith('/api/v1/profile');
  });
});

describe('google health integration endpoints', () => {
  it('fetches the authorization URL to start the connect flow', async () => {
    const api = spyClient();
    (api.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      authorizationUrl: 'https://accounts.google.com/auth',
    });

    const result = await getGoogleHealthConnectUrl(api);

    expect(api.get).toHaveBeenCalledWith('/api/v1/integrations/google-health/connect');
    expect(result).toEqual({ authorizationUrl: 'https://accounts.google.com/auth' });
  });

  it('disconnects the integration', async () => {
    const api = spyClient();

    await disconnectGoogleHealth(api);

    expect(api.del).toHaveBeenCalledWith('/api/v1/integrations/google-health');
  });
});
