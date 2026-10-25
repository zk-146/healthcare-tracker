package com.healthcare.activitytracker.model.enums;

/**
 * The window a goal is scored over.
 *
 * <p>Each constant maps onto one of {@link com.healthcare.activitytracker.service.SummaryService}'s
 * period summaries, so "this week" means exactly the same thing to a goal as it does everywhere
 * else in the app (Monday through today, in the caller's own timezone).
 */
public enum GoalPeriod {
  DAILY,
  WEEKLY,
  MONTHLY
}
