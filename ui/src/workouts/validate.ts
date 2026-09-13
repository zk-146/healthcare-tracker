import type { ActivityInput, ActivityResponse, ActivityType } from '../api/types';

/**
 * The form's own shape: every field a string, because that is what DOM inputs hold.
 * validateDraft is the single crossing point from this to ActivityInput.
 */
export interface WorkoutDraft {
  activityType: ActivityType;
  /** datetime-local value, "YYYY-MM-DDTHH:mm". No seconds, no zone. */
  startedAt: string;
  durationMinutes: string;
  distanceKm: string;
  caloriesBurned: string;
  steps: string;
  heartRateAvg: string;
  notes: string;
}

export type FieldErrors = Partial<Record<keyof WorkoutDraft, string>>;

const OPTIONAL_KEYS = ['distanceKm', 'caloriesBurned', 'steps', 'heartRateAvg', 'notes'] as const;

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** Local wall-clock, not UTC: the backend stores a zoneless LocalDateTime. */
export function toLocalInput(date: Date): string {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

export function emptyDraft(now: Date): WorkoutDraft {
  return {
    activityType: 'WALKING',
    startedAt: toLocalInput(now),
    durationMinutes: '',
    distanceKm: '',
    caloriesBurned: '',
    steps: '',
    heartRateAvg: '',
    notes: '',
  };
}

function numberToField(value: number | null): string {
  return value === null ? '' : String(value);
}

export function draftFrom(activity: ActivityResponse): WorkoutDraft {
  return {
    activityType: activity.activityType,
    startedAt: activity.startedAt.slice(0, 16),
    durationMinutes: numberToField(activity.durationMinutes),
    distanceKm: numberToField(activity.distanceKm),
    caloriesBurned: numberToField(activity.caloriesBurned),
    steps: numberToField(activity.steps),
    heartRateAvg: numberToField(activity.heartRateAvg),
    notes: activity.notes ?? '',
  };
}

export function hasDetails(draft: WorkoutDraft): boolean {
  return OPTIONAL_KEYS.some((key) => draft[key].trim() !== '');
}

interface NumericRule {
  message: string;
  whole: boolean;
  min: number;
  max: number;
  /** true when the minimum itself is not allowed (the backend's inclusive=false). */
  exclusiveMin: boolean;
}

const NUMERIC_RULES: Record<'distanceKm' | 'caloriesBurned' | 'steps' | 'heartRateAvg', NumericRule> = {
  distanceKm: {
    message: 'Distance must be between 0 and 1000 km',
    whole: false,
    min: 0,
    max: 1000,
    exclusiveMin: true,
  },
  caloriesBurned: {
    message: 'Calories must be between 0 and 10000',
    whole: false,
    min: 0,
    max: 10000,
    exclusiveMin: true,
  },
  steps: {
    message: 'Steps must be a whole number between 1 and 100000',
    whole: true,
    min: 1,
    max: 100000,
    exclusiveMin: false,
  },
  heartRateAvg: {
    message: 'Heart rate must be a whole number between 1 and 300 bpm',
    whole: true,
    min: 1,
    max: 300,
    exclusiveMin: false,
  },
};

function parseNumeric(raw: string, rule: NumericRule): number | null {
  const value = Number(raw);
  if (raw.trim() === '' || Number.isNaN(value)) {
    return null;
  }
  if (rule.whole && !Number.isInteger(value)) {
    return null;
  }
  if (rule.exclusiveMin ? value <= rule.min : value < rule.min) {
    return null;
  }
  if (value > rule.max) {
    return null;
  }
  return value;
}

/**
 * Shared by validateDraft (on Save) and WorkoutForm's change handler (on pick): `max` on a
 * datetime-local input only greys out later days, so a later time today is still pickable.
 */
export function startedAtError(startedAt: string, now: Date): string | undefined {
  if (startedAt.trim() === '') {
    return 'Start time is required';
  }
  if (new Date(startedAt).getTime() > now.getTime()) {
    return 'Start time cannot be in the future';
  }
  return undefined;
}

/**
 * The history filters' date pair, both optional. Without this, a future or reversed
 * pair just shows "No workouts match", which reads as missing data rather than a typo.
 * Day keys are zero-padded YYYY-MM-DD, so they order correctly as strings.
 */
export function filterDatesError(
  from: string | undefined,
  to: string | undefined,
  today: string,
): string | null {
  if (from !== undefined && from > today) {
    return "From date can't be in the future.";
  }
  if (to !== undefined && to > today) {
    return "To date can't be in the future.";
  }
  if (from !== undefined && to !== undefined && from > to) {
    return 'From date must be on or before To date.';
  }
  return null;
}

export function validateDraft(
  draft: WorkoutDraft,
  now: Date,
): { errors: FieldErrors; input: ActivityInput | null } {
  const errors: FieldErrors = {};

  const startError = startedAtError(draft.startedAt, now);
  if (startError !== undefined) {
    errors.startedAt = startError;
  }

  const duration = Number(draft.durationMinutes);
  if (draft.durationMinutes.trim() === '') {
    errors.durationMinutes = 'Duration is required';
  } else if (!Number.isInteger(duration) || duration < 1 || duration > 1440) {
    errors.durationMinutes = 'Duration must be between 1 and 1440 minutes';
  }

  // Typed as an open record, not Partial<ActivityInput>: TypeScript rejects a write
  // through a union-typed key on a Partial with heterogeneous value types. Spreading it
  // into the returned literal below is what re-attaches the real types.
  const optional: Record<string, number | string> = {};
  for (const key of ['distanceKm', 'caloriesBurned', 'steps', 'heartRateAvg'] as const) {
    const raw = draft[key];
    if (raw.trim() === '') {
      continue;
    }
    const rule = NUMERIC_RULES[key];
    const value = parseNumeric(raw, rule);
    if (value === null) {
      errors[key] = rule.message;
    } else {
      optional[key] = value;
    }
  }

  const notes = draft.notes.trim();
  if (draft.notes.length > 1000) {
    errors.notes = 'Notes cannot exceed 1000 characters';
  } else if (notes !== '') {
    optional.notes = notes;
  }

  if (Object.keys(errors).length > 0) {
    return { errors, input: null };
  }

  return {
    errors,
    input: {
      activityType: draft.activityType,
      // Seconds appended because the backend parses a full LocalDateTime.
      startedAt: `${draft.startedAt}:00`,
      durationMinutes: duration,
      ...optional,
    },
  };
}
