import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from './client';
import {
  createActivity,
  deleteActivity,
  listAllActivities,
  updateActivity,
} from './endpoints';
import type { ActivityInput } from './types';

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

    await updateActivity(api, 'a1', { ...minimal, steps: 9000 });

    expect(api.put).toHaveBeenCalledWith('/api/v1/activities/a1', {
      activityType: 'RUNNING',
      source: 'MANUAL',
      startedAt: '2026-09-08T07:30:00',
      durationMinutes: 45,
      steps: 9000,
    });
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
