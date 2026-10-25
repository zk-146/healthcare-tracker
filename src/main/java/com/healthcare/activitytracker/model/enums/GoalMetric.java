package com.healthcare.activitytracker.model.enums;

/**
 * What a goal measures. Each constant corresponds to one aggregate already produced by {@link
 * com.healthcare.activitytracker.model.dto.SummaryResponse}, which is why goals need no aggregation
 * of their own.
 *
 * <p>The per-day ceilings are deliberately the same numbers {@link
 * com.healthcare.activitytracker.model.dto.ActivityRequest} caps a single activity at (1440
 * minutes, 1000 km, 100k steps, 10k kcal). A goal therefore cannot be set higher than a day of
 * maximal logging could possibly satisfy — the bound is derived from the write validation rather
 * than picked arbitrarily.
 */
public enum GoalMetric {
  MINUTES("minutes", 1_440),
  SESSIONS("sessions", 24),
  DISTANCE_KM("km", 1_000),
  STEPS("steps", 100_000),
  CALORIES("kcal", 10_000);

  /** Longest period length in days, used to scale {@link #maxTarget(GoalPeriod)}. */
  private static final int MAX_MONTH_DAYS = 31;

  private final String unit;
  private final double perDayMax;

  GoalMetric(String unit, double perDayMax) {
    this.unit = unit;
    this.perDayMax = perDayMax;
  }

  /** Display suffix for this metric's values, e.g. "km". */
  public String getUnit() {
    return unit;
  }

  /**
   * The largest target accepted for this metric over the given period.
   *
   * <p>Scaled by period length because one ceiling cannot serve all three: 500,000 steps is a
   * reasonable monthly target and an impossible daily one. MONTHLY uses 31 days regardless of the
   * actual month — validation runs when the goal is created, which may be months before it is
   * scored, so the bound cannot depend on which month happens to be current.
   */
  public double maxTarget(GoalPeriod period) {
    return perDayMax * periodDays(period);
  }

  private static int periodDays(GoalPeriod period) {
    return switch (period) {
      case DAILY -> 1;
      case WEEKLY -> 7;
      case MONTHLY -> MAX_MONTH_DAYS;
    };
  }
}
