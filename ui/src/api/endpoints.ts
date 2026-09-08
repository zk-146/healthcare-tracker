import { ApiError, type ApiClient } from './client';
import type {
  ActivityInput,
  ActivityResponse,
  AuthResponse,
  GoogleHealthStatusResponse,
  Page,
  SummaryResponse,
} from './types';

/** Today's summary in the caller's timezone. Supplies streakDays for the hero. */
export function getDailySummary(api: ApiClient): Promise<SummaryResponse> {
  return api.get<SummaryResponse>('/api/v1/summary/daily');
}

/**
 * Activities in an inclusive date range. One call backs the chart, the latest-day card,
 * and the recent-days list. `size=100` comfortably exceeds a 7-day window of daily rows.
 */
export function listActivities(
  api: ApiClient,
  from: string,
  to: string,
): Promise<Page<ActivityResponse>> {
  const params = new URLSearchParams({ from, to, size: '100', sort: 'startedAt,desc' });
  return api.get<Page<ActivityResponse>>(`/api/v1/activities?${params.toString()}`);
}

export function getSyncStatus(api: ApiClient): Promise<GoogleHealthStatusResponse> {
  return api.get<GoogleHealthStatusResponse>('/api/v1/integrations/google-health/status');
}

/** Login runs before any token exists, so it bypasses the authenticated client. */
export async function login(
  email: string,
  password: string,
  fetchImpl: typeof fetch = fetch,
): Promise<AuthResponse> {
  // Global constraint: every API call sends X-User-Timezone, including pre-auth calls.
  // Do not simplify this back to a bare Content-Type header.
  const response = await fetchImpl('/api/v1/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Timezone': Intl.DateTimeFormat().resolvedOptions().timeZone,
    },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    let body = null;
    try {
      body = await response.json();
    } catch {
      body = null;
    }
    throw new ApiError(response.status, body);
  }
  return (await response.json()) as AuthResponse;
}

/** Blacklists the access token server-side rather than merely discarding it. */
export async function logout(api: ApiClient): Promise<void> {
  await api.post<void>('/api/v1/auth/logout');
}

const OPTIONAL_FIELDS = [
  'distanceKm',
  'caloriesBurned',
  'steps',
  'heartRateAvg',
  'notes',
] as const;

/** Global constraint: source is hard-coded MANUAL and endedAt is never sent. */
function activityBody(input: ActivityInput): Record<string, unknown> {
  const body: Record<string, unknown> = {
    activityType: input.activityType,
    source: 'MANUAL',
    startedAt: input.startedAt,
    durationMinutes: input.durationMinutes,
  };
  for (const field of OPTIONAL_FIELDS) {
    const value = input[field];
    if (value !== undefined) {
      body[field] = value;
    }
  }
  return body;
}

export function createActivity(api: ApiClient, input: ActivityInput): Promise<ActivityResponse> {
  return api.post<ActivityResponse>('/api/v1/activities', activityBody(input));
}

export function updateActivity(
  api: ApiClient,
  id: string,
  input: ActivityInput,
): Promise<ActivityResponse> {
  return api.put<ActivityResponse>(`/api/v1/activities/${id}`, activityBody(input));
}

export function deleteActivity(api: ApiClient, id: string): Promise<void> {
  return api.del<void>(`/api/v1/activities/${id}`);
}

/**
 * Full history, newest first — unlike listActivities, which windows by date for the
 * dashboard. Page size 20 keeps the first paint small on a phone.
 */
export function listAllActivities(api: ApiClient, page: number): Promise<Page<ActivityResponse>> {
  const params = new URLSearchParams({
    page: String(page),
    size: '20',
    sort: 'startedAt,desc',
  });
  return api.get<Page<ActivityResponse>>(`/api/v1/activities?${params.toString()}`);
}
