package com.healthcare.activitytracker.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Progress toward the next streak milestone.
 *
 * <p>{@code currentStreak} is computed in the caller's timezone, while milestones are awarded on
 * UTC boundaries by {@code ActivityEventConsumer}. Near midnight the two can disagree by a day.
 * This split predates the endpoint and is documented in {@code AUDIT.md} (M8).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MilestoneProgressResponse {

  /** The user's current streak in days, in their own timezone. */
  private int currentStreak;

  /** The next streak length that earns a badge, or {@code null} once the ladder is exhausted. */
  private Integer nextThreshold;

  /** Days still needed to reach {@code nextThreshold}, or {@code null} when it is null. */
  private Integer daysRemaining;

  /** Fraction of the way to the next rung, 0..1. {@code 1.0} once the ladder is exhausted. */
  private double progress;

  /** Every threshold in the ladder, ascending — so clients need not hardcode it. */
  private List<Integer> ladder;
}
