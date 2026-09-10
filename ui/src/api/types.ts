export type ActivityType =
  | 'WALKING'
  | 'RUNNING'
  | 'YOGA'
  | 'CYCLING'
  | 'SWIMMING'
  | 'STRENGTH_TRAINING'
  | 'STRETCHING'
  | 'OTHER';

/** Watch/Google Health sync writes IOT. There is no GOOGLE_HEALTH value. */
export type ActivitySource = 'MANUAL' | 'IOT' | 'CSV_IMPORT';

export type ConnectionStatus = 'CONNECTED' | 'NEEDS_RECONNECT';

export interface ActivityResponse {
  id: string;
  activityType: ActivityType;
  source: ActivitySource;
  deviceId: string | null;
  /** Zoneless LocalDateTime, e.g. "2026-08-22T00:00:00". */
  startedAt: string;
  endedAt: string | null;
  durationMinutes: number | null;
  distanceKm: number | null;
  caloriesBurned: number | null;
  heartRateAvg: number | null;
  steps: number | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ActivityTypeSummary {
  type: string;
  source: string;
  count: number;
  totalMinutes: number;
  totalCalories: number;
}

export interface SummaryResponse {
  from: string;
  to: string;
  totalActivities: number;
  totalDurationMinutes: number;
  totalCaloriesBurned: number;
  totalDistanceKm: number;
  totalSteps: number;
  streakDays: number;
  averageDailyCalories: number;
  bySource: Record<string, number>;
  byActivityType: ActivityTypeSummary[];
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
  expiresIn: number;
  userId: string;
  email: string;
}

export interface GoogleHealthStatusResponse {
  connected: boolean;
  status: ConnectionStatus | null;
  lastSyncedAt: string | null;
}

export interface GoogleHealthConnectResponse {
  authorizationUrl: string;
}

export type SummaryPeriod = 'daily' | 'weekly' | 'monthly';

/** Response to a Fitbit dailyActivity_merged.csv import. */
export interface CsvImportResponse {
  fileName: string;
  totalRows: number;
  imported: number;
  duplicatesSkipped: number;
  failed: number;
  /** Row-level error messages, capped to a bounded prefix by the backend. */
  errors: string[];
}

export interface DigestResponse {
  period: string;
  from: string;
  to: string;
  /** False when the LLM backing the digest is unavailable — `digest` is still a
   *  human-readable fallback message in that case, never an error. */
  available: boolean;
  digest: string;
}

export interface ProfileResponse {
  id: string;
  email: string;
  fullName: string;
  /** LocalDate, "YYYY-MM-DD". */
  dateOfBirth: string | null;
  gender: string | null;
  heightCm: number | null;
  weightKg: number | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * The backend's PUT is a partial update — a field left out of the body is left
 * unchanged, unlike ActivityRequest's full-replace semantics. There is no way to
 * clear a field back to null through this endpoint.
 */
export interface ProfileUpdateInput {
  fullName?: string;
  dateOfBirth?: string;
  gender?: string;
  heightCm?: number;
  weightKg?: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** GlobalExceptionHandler bodies, plus the 401 shape from the auth entry point. */
export interface ApiErrorBody {
  status?: number;
  error: string;
  details?: Record<string, string>;
  timestamp?: string;
}

/**
 * The subset of ActivityRequest a manual workout supplies. `source` is always
 * MANUAL and `endedAt` is never sent: the backend's @ValidDateRange rule requires
 * endedAt and durationMinutes to agree when both are present, and the form only
 * collects duration.
 */
/** Optional filters for GET /activities, all applied server-side and combinable. */
export interface ActivityFilters {
  activityType?: ActivityType;
  /** Inclusive, "YYYY-MM-DD". */
  from?: string;
  to?: string;
}

export interface ActivityInput {
  activityType: ActivityType;
  /** Zoneless LocalDateTime, "YYYY-MM-DDTHH:mm:ss". */
  startedAt: string;
  durationMinutes: number;
  distanceKm?: number;
  caloriesBurned?: number;
  steps?: number;
  heartRateAvg?: number;
  notes?: string;
}
