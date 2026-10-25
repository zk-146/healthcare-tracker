package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.model.enums.GoalPeriod;
import com.healthcare.activitytracker.validation.ValidGoalTarget;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Creates a goal. The metric/period pair is the goal's identity and cannot be changed later. */
@Data
@ValidGoalTarget
public class GoalRequest {

  @NotNull(message = "Metric is required")
  private GoalMetric metric;

  @NotNull(message = "Period is required")
  private GoalPeriod period;

  /**
   * Upper bound is per-metric and per-period, so it cannot be expressed with {@code @DecimalMax}
   * here — {@link ValidGoalTarget} enforces it against {@link GoalMetric#maxTarget(GoalPeriod)}.
   */
  @NotNull(message = "Target is required")
  private Double targetValue;
}
