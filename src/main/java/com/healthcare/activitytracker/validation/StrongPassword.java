package com.healthcare.activitytracker.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that a password meets healthcare-grade complexity requirements: - Minimum 12 characters
 * - Maximum 128 characters - At least one uppercase letter - At least one lowercase letter - At
 * least one digit - At least one special character
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
  String message() default
      "Password must be 12–128 characters and include uppercase, lowercase, digit, and special character";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
