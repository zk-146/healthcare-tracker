package com.healthcare.activitytracker.validation;

import com.healthcare.activitytracker.model.dto.GoalRequest;
import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.model.enums.GoalPeriod;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class GoalTargetValidator implements ConstraintValidator<ValidGoalTarget, GoalRequest> {

  @Override
  public boolean isValid(GoalRequest request, ConstraintValidatorContext context) {
    Double target = request.getTargetValue();
    GoalMetric metric = request.getMetric();
    GoalPeriod period = request.getPeriod();

    // @NotNull on each field owns the null cases; reporting them twice would put two
    // messages on one field and the second would overwrite the first in the details map.
    if (target == null || metric == null || period == null) {
      return true;
    }

    String message = messageFor(target, metric, period);
    if (message == null) {
      return true;
    }

    if (context != null) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(message)
          // Names the field so GlobalExceptionHandler's details map keys it as
          // "targetValue" and the UI can attach the error to the right input.
          .addPropertyNode("targetValue")
          .addConstraintViolation();
    }
    return false;
  }

  /**
   * The violated-rule message, or null when the target is acceptable.
   *
   * <p>Public because {@link com.healthcare.activitytracker.service.GoalService} enforces the same
   * bound on update, where the metric comes from the stored row and Bean Validation cannot reach
   * it. One definition of the rule, two call sites.
   */
  public static String messageFor(double target, GoalMetric metric, GoalPeriod period) {
    if (target <= 0) {
      return "Target must be greater than 0";
    }
    double max = metric.maxTarget(period);
    if (target > max) {
      return "Target cannot exceed "
          + formatBound(max)
          + " "
          + metric.getUnit()
          + " for a "
          + period.name().toLowerCase(java.util.Locale.ROOT)
          + " goal";
    }
    return null;
  }

  /** Bounds are whole numbers by construction; render them without a trailing ".0". */
  private static String formatBound(double bound) {
    return String.valueOf((long) bound);
  }
}
