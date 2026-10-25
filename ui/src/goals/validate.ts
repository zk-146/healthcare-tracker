import type { GoalInput, GoalMetric, GoalPeriod } from '../api/types';
import { isWholeMetric, maxTarget, metricUnit } from '../lib/goals';

/**
 * The form's own shape: targetValue is a string because that is what a DOM input holds.
 * validateDraft is the single crossing point from this to GoalInput, same convention as
 * workouts/validate.ts.
 */
export interface GoalDraft {
  metric: GoalMetric;
  period: GoalPeriod;
  targetValue: string;
}

export type FieldErrors = Partial<Record<keyof GoalDraft, string>>;

export function emptyDraft(): GoalDraft {
  // Weekly is the default on purpose: a daily target is as unforgiving as the streak goals
  // are meant to soften, and a month is too long to read as feedback.
  return { metric: 'MINUTES', period: 'WEEKLY', targetValue: '' };
}

/**
 * Validates a target on its own, shared by the create form and the inline target edit.
 * Returns an error message, or null when the value is acceptable.
 */
export function targetError(raw: string, metric: GoalMetric, period: GoalPeriod): string | null {
  if (raw.trim() === '') {
    return 'Target is required';
  }

  const value = Number(raw);
  if (Number.isNaN(value)) {
    return 'Target must be a number';
  }
  if (value <= 0) {
    return 'Target must be greater than 0';
  }
  if (isWholeMetric(metric) && !Number.isInteger(value)) {
    return `Target must be a whole number of ${metricUnit(metric)}`;
  }

  const max = maxTarget(metric, period);
  if (value > max) {
    return `Target cannot exceed ${max.toLocaleString('en-GB')} ${metricUnit(
      metric,
    )} for a ${period.toLowerCase()} goal`;
  }

  return null;
}

export function validateDraft(draft: GoalDraft): { errors: FieldErrors; input: GoalInput | null } {
  const errors: FieldErrors = {};

  const error = targetError(draft.targetValue, draft.metric, draft.period);
  if (error !== null) {
    errors.targetValue = error;
  }

  if (Object.keys(errors).length > 0) {
    return { errors, input: null };
  }

  return {
    errors,
    input: {
      metric: draft.metric,
      period: draft.period,
      targetValue: Number(draft.targetValue),
    },
  };
}
