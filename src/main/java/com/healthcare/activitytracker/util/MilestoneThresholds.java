package com.healthcare.activitytracker.util;

import java.util.List;
import java.util.Optional;

/**
 * The streak lengths, in days, that earn a milestone badge.
 *
 * <p>Single source of truth, shared by {@code ActivityEventConsumer}, which awards them, and {@code
 * MilestoneService}, which reports progress toward the next one. This was previously a private
 * constant on the consumer, which meant nothing over HTTP could report the full ladder — the next
 * unearned threshold by definition has no row in {@code GET /api/v1/milestones}.
 */
public final class MilestoneThresholds {

  /**
   * Ascending. The order is relied upon in two places: picking the highest threshold crossed when a
   * backfill awards several at once, and finding the first threshold above the current streak.
   */
  public static final List<Integer> ALL = List.of(3, 7, 14, 30, 60, 100, 365);

  private MilestoneThresholds() {}

  /**
   * The first threshold strictly above {@code streakDays}, or empty once the ladder is exhausted.
   *
   * @param streakDays the user's current streak
   */
  public static Optional<Integer> next(int streakDays) {
    return ALL.stream().filter(threshold -> threshold > streakDays).findFirst();
  }
}
