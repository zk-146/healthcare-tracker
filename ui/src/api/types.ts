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
