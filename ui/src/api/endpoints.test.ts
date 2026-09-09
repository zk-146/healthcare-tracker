import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from './client';
import {
  createActivity,
  deleteActivity,
  listAllActivities,
  updateActivity,
} from './endpoints';
import type { ActivityInput, ActivityResponse } from './types';

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
