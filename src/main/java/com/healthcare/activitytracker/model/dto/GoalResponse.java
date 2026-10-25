package com.healthcare.activitytracker.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A goal plus where the user currently stands against it. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoalResponse {

  private UUID id;

  /** One of: MINUTES, SESSIONS, DISTANCE_KM, STEPS, CALORIES. */
  private String metric;

  /** One of: DAILY, WEEKLY, MONTHLY. */
  private String period;

  private Double targetValue;

  /** Display suffix for the values, e.g. "km". */
  private String unit;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  private Progress progress;

  /**
   * Standing against the goal for the period in progress, in the caller's timezone.
   *
   * <p>The pace fields ({@code expectedByNow}, {@code onTrack}, {@code daysRemaining}) are the
   * point of the feature: a bare percentage cannot distinguish 40% on a Tuesday, which is ahead,
   * from 40% on a Sunday, which is not.
   */
  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Progress {

    /** Inclusive start of the period in progress. */
    private LocalDate from;

    /** Today, in the caller's timezone — not the period's end. */
    private LocalDate to;

    private double currentValue;

    /** 0–100, uncapped: exceeding a goal reads as 150%, not 100%. */
    private double percentComplete;

    private boolean achieved;

    /** Days of the period gone by, inclusive of today. Always >= 1. */
    private int daysElapsed;

    /** Length of the whole period in days (1, 7, or the current month's length). */
    private int daysTotal;

    /** Days left after today. Zero on the period's final day. */
    private int daysRemaining;

    /** Where a perfectly even pace would have the user by now. */
    private double expectedByNow;

    private boolean onTrack;
  }
}
