import { toDayKey } from '../lib/days';

/** Mirrors app.summary.max-range-days (application.yml). The backend stays authoritative. */
export const MAX_RANGE_DAYS = 365;

const DEFAULT_RANGE_DAYS = 30;
const MS_PER_DAY = 86_400_000;

export interface DateRange {
  /** 'YYYY-MM-DD', inclusive. */
  from: string;
  /** 'YYYY-MM-DD', inclusive. */
  to: string;
}

/** Built from parts so the browser never reinterprets the day key as UTC. */
function parseDayKey(dayKey: string): Date {
  const [year, month, day] = dayKey.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/** The 30 days ending on `today`, inclusive. */
export function defaultRange(today: Date): DateRange {
  const start = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate() - (DEFAULT_RANGE_DAYS - 1),
  );
  return { from: toDayKey(start), to: toDayKey(today) };
}

/**
 * Returns the first problem with the range, or null when it is fine to request.
 * "Days between" matches the backend's ChronoUnit.DAYS.between, so a 365-day gap
 * (e.g. 2025-01-01 → 2026-01-01) is allowed and one more day is not.
 */
export function validateRange(from: string, to: string): string | null {
  if (from === '' || to === '') {
    return 'Pick both a start and end date.';
  }
  // Math.round absorbs the ±1h a DST change puts between two local midnights.
  const days = Math.round((parseDayKey(to).getTime() - parseDayKey(from).getTime()) / MS_PER_DAY);
  if (days < 0) {
    return 'Start date must be on or before end date.';
  }
  if (days > MAX_RANGE_DAYS) {
    return `Range can't be longer than ${MAX_RANGE_DAYS} days.`;
  }
  return null;
}
