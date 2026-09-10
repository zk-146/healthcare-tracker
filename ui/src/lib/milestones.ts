/**
 * Mirrors MILESTONE_THRESHOLDS in
 * src/main/java/com/healthcare/activitytracker/service/ActivityEventConsumer.java:37
 *
 * This is knowingly a second source of truth. GET /api/v1/milestones (see
 * api/endpoints.ts's getMilestones) reports what the user has already earned, but
 * nothing over HTTP reports the full ladder — StreakHero needs to know the *next*
 * (unearned) threshold too, which by definition has no row in that response. Keep
 * this list in sync with the backend's.
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
