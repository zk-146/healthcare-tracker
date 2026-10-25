package com.healthcare.activitytracker.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Changes an existing goal's target.
 *
 * <p>Metric and period are absent by design: they form the key the active-goal uniqueness index is
 * built on, so changing either is a different goal. Retire this one and create that one.
 *
 * <p>The per-metric ceiling cannot be checked here either — it depends on the metric of the stored
 * row, which Bean Validation never sees. {@link com.healthcare.activitytracker.service.GoalService}
 * enforces it and raises {@link com.healthcare.activitytracker.exception.FieldValidationException}
 * so the 400 keeps the same {@code details: {field: message}} shape a create failure produces.
 */
@Data
public class GoalUpdateRequest {

  @NotNull(message = "Target is required")
  private Double targetValue;
}
