package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.model.enums.GoalMetric;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoalRequest {

  @NotNull(message = "metric is required")
  private GoalMetric metric;

  @Positive(message = "targetValue must be positive")
  private double targetValue;
}
