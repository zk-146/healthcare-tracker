import { ApiError, type ApiClient } from './client';
import type {
  ActivityFilters,
  ActivityInput,
  ActivityResponse,
  AuthResponse,
  CsvImportResponse,
  DigestResponse,
  GoogleHealthConnectResponse,
  GoogleHealthStatusResponse,
  MilestoneResponse,
  Page,
  ProfileResponse,
  ProfileUpdateInput,
  SummaryPeriod,
  SummaryResponse,
} from './types';

/** Today's summary in the caller's timezone. Supplies streakDays for the hero. */
export function getDailySummary(api: ApiClient): Promise<SummaryResponse> {
  return api.get<SummaryResponse>('/api/v1/summary/daily');
}

/** Week-to-date (Monday through today) in the caller's timezone. */
export function getWeeklySummary(api: ApiClient): Promise<SummaryResponse> {
  return api.get<SummaryResponse>('/api/v1/summary/weekly');
}

/** Month-to-date (1st through today) in the caller's timezone. */
export function getMonthlySummary(api: ApiClient): Promise<SummaryResponse> {
  return api.get<SummaryResponse>('/api/v1/summary/monthly');
}

export function getSummaryFor(api: ApiClient, period: SummaryPeriod): Promise<SummaryResponse> {
  if (period === 'daily') {
    return getDailySummary(api);
  }
  if (period === 'weekly') {
    return getWeeklySummary(api);
  }
  return getMonthlySummary(api);
}

/**
 * An AI-generated natural-language recap. Never rejects on the LLM being unavailable —
 * check `available` on the response, which already carries a human-readable fallback
 * message in `digest` either way.
 */
export function getDigest(api: ApiClient, period: SummaryPeriod): Promise<DigestResponse> {
  return api.get<DigestResponse>(`/api/v1/summary/digest?period=${period}`);
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

/**
 * Starts the link flow: the caller opens the returned URL in a browser tab, approves
 * access with Google, and is redirected to the backend's (non-SPA) callback page. There
 * is nothing to await here beyond getting that URL — completion is out of band.
 */
export function getGoogleHealthConnectUrl(api: ApiClient): Promise<GoogleHealthConnectResponse> {
  return api.get<GoogleHealthConnectResponse>('/api/v1/integrations/google-health/connect');
}

export function disconnectGoogleHealth(api: ApiClient): Promise<void> {
  return api.del<void>('/api/v1/integrations/google-health');
}

/** Shared by login and register — neither has a token yet, so both bypass the ApiClient. */
async function unauthenticatedPost(
  path: string,
  body: unknown,
  fetchImpl: typeof fetch,
): Promise<AuthResponse> {
  // Global constraint: every API call sends X-User-Timezone, including pre-auth calls.
  // Do not simplify this back to a bare Content-Type header.
  const response = await fetchImpl(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Timezone': Intl.DateTimeFormat().resolvedOptions().timeZone,
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    let errorBody = null;
    try {
      errorBody = await response.json();
    } catch {
      errorBody = null;
    }
    throw new ApiError(response.status, errorBody);
  }
  return (await response.json()) as AuthResponse;
}

/** Login runs before any token exists, so it bypasses the authenticated client. */
export function login(
  email: string,
  password: string,
  fetchImpl: typeof fetch = fetch,
): Promise<AuthResponse> {
  return unauthenticatedPost('/api/v1/auth/login', { email, password }, fetchImpl);
}

/**
 * Registers a new account and returns the same token pair as login, so the caller can
 * sign the user straight in without a second round trip.
 */
export function register(
  email: string,
  password: string,
  fullName: string,
  fetchImpl: typeof fetch = fetch,
): Promise<AuthResponse> {
  return unauthenticatedPost('/api/v1/auth/register', { email, password, fullName }, fetchImpl);
}

/** Blacklists the access token server-side rather than merely discarding it. */
export async function logout(api: ApiClient): Promise<void> {
  await api.post<void>('/api/v1/auth/logout');
}

/**
 * Revokes every refresh token for the account server-side; the caller's current
 * access token is unaffected and stays valid until it naturally expires.
 *
 * `retryOn401: false` because this endpoint returns 401 for "current password is
 * incorrect" — a business-logic error, not an expired token. Without it, ApiClient's
 * default 401 handling would refresh the (perfectly valid) token, retry, get the same
 * 401 again, and force-sign the user out instead of surfacing the real error.
 */
export async function changePassword(
  api: ApiClient,
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  await api.post<void>(
    '/api/v1/auth/change-password',
    { currentPassword, newPassword },
    { retryOn401: false },
  );
}

const OPTIONAL_FIELDS = [
  'distanceKm',
  'caloriesBurned',
  'steps',
  'heartRateAvg',
  'notes',
] as const;

/**
 * Builds the shared create/update body from a draft. Source is hard-coded MANUAL and
 * endedAt is never sent here — correct for createActivity (a new activity has no prior
 * source to preserve). updateActivity overlays the original's source/deviceId/endedAt
 * on top of this before sending, so a non-MANUAL row keeps its provenance on edit.
 */
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

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/**
 * Adds `durationMinutes` minutes to a zoneless "YYYY-MM-DDTHH:mm:ss" string, returning
 * a zoneless string in the same shape. Built by hand (not `new Date(...).toISOString()`)
 * because the backend's LocalDateTime has no zone and toISOString() would introduce UTC.
 */
function computedEndOf(startedAt: string, durationMinutes: number): string {
  const [datePart, timePart] = startedAt.split('T');
  const [year, month, day] = datePart.split('-').map(Number);
  const [hour, minute, second] = timePart.split(':').map(Number);
  const local = new Date(year, month - 1, day, hour, minute, second);
  local.setMinutes(local.getMinutes() + durationMinutes);
  return (
    `${local.getFullYear()}-${pad(local.getMonth() + 1)}-${pad(local.getDate())}` +
    `T${pad(local.getHours())}:${pad(local.getMinutes())}:${pad(local.getSeconds())}`
  );
}

/**
 * Unlike createActivity, an update must carry the original activity's source and
 * deviceId through untouched: the backend's PUT is a full field replace, and a row
 * with source CSV_IMPORT/IOT (and its deviceId) must not be silently flipped to
 * MANUAL/null just because the user edited an unrelated field. endedAt is echoed
 * back only when it is still consistent with the (possibly edited) startedAt/
 * durationMinutes being submitted; otherwise it is omitted, same as before.
 */
export function updateActivity(
  api: ApiClient,
  id: string,
  input: ActivityInput,
  original: ActivityResponse,
): Promise<ActivityResponse> {
  const body = activityBody(input);
  body.source = original.source;
  if (original.deviceId !== null) {
    body.deviceId = original.deviceId;
  }
  if (
    original.endedAt !== null &&
    original.endedAt === computedEndOf(input.startedAt, input.durationMinutes)
  ) {
    body.endedAt = original.endedAt;
  }
  return api.put<ActivityResponse>(`/api/v1/activities/${id}`, body);
}

export function deleteActivity(api: ApiClient, id: string): Promise<void> {
  return api.del<void>(`/api/v1/activities/${id}`);
}

/**
 * Full history, newest first — unlike listActivities, which windows by date for the
 * dashboard. Page size 20 keeps the first paint small on a phone.
 */
export function listAllActivities(
  api: ApiClient,
  page: number,
  filters: ActivityFilters = {},
): Promise<Page<ActivityResponse>> {
  const params = new URLSearchParams({
    page: String(page),
    size: '20',
    sort: 'startedAt,desc',
  });
  if (filters.activityType !== undefined) {
    params.set('activityType', filters.activityType);
  }
  if (filters.from !== undefined) {
    params.set('from', filters.from);
  }
  if (filters.to !== undefined) {
    params.set('to', filters.to);
  }
  return api.get<Page<ActivityResponse>>(`/api/v1/activities?${params.toString()}`);
}

/** Imports a Fitbit `dailyActivity_merged.csv` export. Rows already imported are
 *  skipped server-side; malformed rows are skipped and reported, not fatal. */
export function importFitbitCsv(api: ApiClient, file: File): Promise<CsvImportResponse> {
  const form = new FormData();
  form.append('file', file);
  return api.postForm<CsvImportResponse>('/api/v1/activities/import/fitbit', form);
}

/** Every streak milestone the caller has earned, longest streak first. */
export function getMilestones(api: ApiClient): Promise<MilestoneResponse[]> {
  return api.get<MilestoneResponse[]>('/api/v1/milestones');
}

export function getProfile(api: ApiClient): Promise<ProfileResponse> {
  return api.get<ProfileResponse>('/api/v1/profile');
}

export function updateProfile(
  api: ApiClient,
  input: ProfileUpdateInput,
): Promise<ProfileResponse> {
  return api.put<ProfileResponse>('/api/v1/profile', input);
}

/**
 * Irreversible: deletes the account and all its data server-side. The caller is
 * responsible for clearing the local session afterwards — this call alone leaves
 * the (now-revoked) access token sitting in storage.
 */
export function deleteAccount(api: ApiClient): Promise<void> {
  return api.del<void>('/api/v1/profile');
}
