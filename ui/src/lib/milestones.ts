/**
 * Mirrors MILESTONE_THRESHOLDS in
 * src/main/java/com/healthcare/activitytracker/service/ActivityEventConsumer.java:37
 *
 * This is knowingly a second source of truth. Nothing exposes the ladder over HTTP
 * and StreakMilestone has no controller. Keep in sync until GET /api/v1/milestones exists.
 */
export const MILESTONE_THRESHOLDS = [3, 7, 14, 30, 60, 100, 365] as const;

export interface NextMilestone {
  /** The next streak length that earns a badge. */
  threshold: number;
  /** Days still needed to reach it. Always >= 1. */
  daysRemaining: number;
  /** Fraction of the way there, 0..1, for the progress bar. */
  progress: number;
}

/** Returns the next unearned milestone, or null once the ladder is exhausted. */
export function nextMilestone(streakDays: number): NextMilestone | null {
  const threshold = MILESTONE_THRESHOLDS.find((t) => t > streakDays);
  if (threshold === undefined) {
    return null;
  }
  return {
    threshold,
    daysRemaining: threshold - streakDays,
    progress: Math.max(0, streakDays) / threshold,
  };
}
