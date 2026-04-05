package com.healthcare.activitytracker.repository.projection;

import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;

/** Interface projection for per-type/source activity aggregates. */
public interface ActivityTypeSummaryProjection {
  ActivityType getActivityType();

  ActivitySource getSource();

  Long getCount();

  Long getTotalMinutes();

  Double getTotalCalories();
}
