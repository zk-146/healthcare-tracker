import type { GoalMetric, GoalPeriod, GoalResponse } from '../api/types';

export const GOAL_METRICS = ['MINUTES', 'SESSIONS', 'DISTANCE_KM', 'STEPS', 'CALORIES'] as const;

export const GOAL_PERIODS = ['DAILY', 'WEEKLY', 'MONTHLY'] as const;

/**
 * Mirrors the per-day ceilings on GoalMetric in
 * src/main/java/com/healthcare/activitytracker/model/enums/GoalMetric.java
 *
 * Knowingly a second source of truth, for the same reason MILESTONE_THRESHOLDS is one: the
 * create form has to bound its input before any request is made, and nothing over HTTP reports
 * the limits. The backend remains the authority — a target that slips past this lands as a 400
 * with `details.targetValue`, which the form displays. Keep the two in step.
 */
const PER_DAY_MAX: Record<GoalMetric, number> = {
  MINUTES: 1_440,
  SESSIONS: 24,
  DISTANCE_KM: 1_000,
  STEPS: 100_000,
  CALORIES: 10_000,
};

/** MONTHLY uses 31 to match the backend, which cannot know which month a goal will be scored in. */
const PERIOD_DAYS: Record<GoalPeriod, number> = { DAILY: 1, WEEKLY: 7, MONTHLY: 31 };

/** Metrics counted in whole units — a fractional step or workout is meaningless. */
const WHOLE_METRICS: readonly GoalMetric[] = ['MINUTES', 'SESSIONS', 'STEPS'];

const METRIC_LABELS: Record<GoalMetric, string> = {
  MINUTES: 'Active minutes',
  SESSIONS: 'Workouts',
  DISTANCE_KM: 'Distance',
  STEPS: 'Steps',
  CALORIES: 'Calories',
};

/** Unit suffixes for the create form, which has no server response to read `unit` from. */
const METRIC_UNITS: Record<GoalMetric, string> = {
  MINUTES: 'minutes',
  SESSIONS: 'sessions',
  DISTANCE_KM: 'km',
  STEPS: 'steps',
  CALORIES: 'kcal',
};

const PERIOD_LABELS: Record<GoalPeriod, string> = {
  DAILY: 'Daily',
  WEEKLY: 'Weekly',
  MONTHLY: 'Monthly',
};

export function metricLabel(metric: GoalMetric): string {
  return METRIC_LABELS[metric];
}

export function metricUnit(metric: GoalMetric): string {
  return METRIC_UNITS[metric];
}

export function periodLabel(period: GoalPeriod): string {
  return PERIOD_LABELS[period];
}

export function isWholeMetric(metric: GoalMetric): boolean {
  return WHOLE_METRICS.includes(metric);
}

export function maxTarget(metric: GoalMetric, period: GoalPeriod): number {
  return PER_DAY_MAX[metric] * PERIOD_DAYS[period];
}

/** e.g. "Weekly active minutes". */
export function goalTitle(metric: GoalMetric, period: GoalPeriod): string {
  return `${PERIOD_LABELS[period]} ${METRIC_LABELS[metric].toLowerCase()}`;
}

/**
 * Whole metrics render with thousands separators and no decimals; the rest keep one decimal
 * unless it is zero. The locale is pinned for the same reason days.ts pins it: left to the host
 * the same number renders differently per viewer and per CI runner.
 */
export function formatValue(value: number, metric: GoalMetric): string {
  if (isWholeMetric(metric)) {
    return Math.round(value).toLocaleString('en-GB');
  }
  const oneDecimal = Math.round(value * 10) / 10;
  return Number.isInteger(oneDecimal)
    ? oneDecimal.toLocaleString('en-GB')
    : oneDecimal.toLocaleString('en-GB', {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      });
}

/** Clamped to 0–100 for the bar's width; percentComplete itself stays uncapped for the label. */
export function barFraction(goal: GoalResponse): number {
  return Math.min(1, Math.max(0, goal.progress.percentComplete / 100));
}

function daysPhrase(daysRemaining: number): string {
  if (daysRemaining === 0) {
    return 'on the last day';
  }
  return `with ${daysRemaining} ${daysRemaining === 1 ? 'day' : 'days'} left`;
}

/**
 * The one-line verdict under each bar.
 *
 * Says how far off an even pace the user is rather than only how complete they are, because the
 * bare percentage cannot separate 40% on a Tuesday (ahead) from 40% on a Sunday (behind) — which
 * is the whole reason goals beat a streak at surviving a missed day.
 */
export function paceNote(goal: GoalResponse): string {
  const { progress, metric, targetValue, unit } = goal;

  if (progress.achieved) {
    return `Goal met — ${formatValue(progress.currentValue, metric)} of ${formatValue(
      targetValue,
      metric,
    )} ${unit}.`;
  }

  const toGo = formatValue(Math.max(0, targetValue - progress.currentValue), metric);

  if (progress.onTrack) {
    return `On track, ${toGo} ${unit} to go ${daysPhrase(progress.daysRemaining)}.`;
  }

  const behind = formatValue(
    Math.max(0, progress.expectedByNow - progress.currentValue),
    metric,
  );
  return `${behind} ${unit} behind pace, ${toGo} to go ${daysPhrase(progress.daysRemaining)}.`;
}
