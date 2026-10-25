package com.healthcare.activitytracker.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Class-level because the rule is cross-field: the ceiling a target is checked against depends on
 * the metric and the period alongside it.
 */
@Documented
@Constraint(validatedBy = GoalTargetValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGoalTarget {
  String message() default "Target is out of range for this metric";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
