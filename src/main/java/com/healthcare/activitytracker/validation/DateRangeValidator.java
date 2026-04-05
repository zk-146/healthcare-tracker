package com.healthcare.activitytracker.validation;

import com.healthcare.activitytracker.model.dto.ActivityRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.temporal.ChronoUnit;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, ActivityRequest> {

  /**
   * Maximum allowed discrepancy (minutes) between the declared durationMinutes and the actual
   * elapsed time (endedAt − startedAt). A tolerance of 60 minutes accommodates legitimate cases
   * such as GPS-pause, rest intervals, or rounding by fitness devices.
   */
  private static final long DURATION_TOLERANCE_MINUTES = 60;

  @Override
  public boolean isValid(ActivityRequest request, ConstraintValidatorContext context) {
    if (request.getStartedAt() == null) {
      return true; // @NotNull on startedAt handles the null case
    }

    // endedAt must be strictly after startedAt (equal is also invalid — zero-duration activity)
    if (request.getEndedAt() != null && !request.getEndedAt().isAfter(request.getStartedAt())) {
      if (context != null) {
        context.disableDefaultConstraintViolation();
        context
            .buildConstraintViolationWithTemplate("End time must be after start time")
            .addPropertyNode("endedAt")
            .addConstraintViolation();
      }
      return false;
    }

    // durationMinutes must be consistent with endedAt − startedAt when both are present
    if (request.getEndedAt() != null && request.getDurationMinutes() != null) {
      long elapsedMinutes =
          ChronoUnit.MINUTES.between(request.getStartedAt(), request.getEndedAt());
      long declared = request.getDurationMinutes();
      if (declared > elapsedMinutes + DURATION_TOLERANCE_MINUTES) {
        if (context != null) {
          context.disableDefaultConstraintViolation();
          context
              .buildConstraintViolationWithTemplate(
                  "Duration ("
                      + declared
                      + " min) is inconsistent with the time between "
                      + "start and end ("
                      + elapsedMinutes
                      + " min)")
              .addPropertyNode("durationMinutes")
              .addConstraintViolation();
        }
        return false;
      }
    }

    return true;
  }
}
