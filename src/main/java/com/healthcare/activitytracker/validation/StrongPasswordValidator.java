package com.healthcare.activitytracker.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Enforces password complexity rules suitable for healthcare applications. */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

  private static final int MIN_LENGTH = 12;
  private static final int MAX_LENGTH = 128;

  @Override
  public boolean isValid(String password, ConstraintValidatorContext context) {
    if (password == null || password.isBlank()) {
      // Let @NotBlank handle null/blank — don't double-report
      return true;
    }

    boolean valid = true;
    context.disableDefaultConstraintViolation();

    if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
      context
          .buildConstraintViolationWithTemplate(
              "Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters")
          .addConstraintViolation();
      valid = false;
    }

    if (!password.chars().anyMatch(Character::isUpperCase)) {
      context
          .buildConstraintViolationWithTemplate(
              "Password must contain at least one uppercase letter")
          .addConstraintViolation();
      valid = false;
    }

    if (!password.chars().anyMatch(Character::isLowerCase)) {
      context
          .buildConstraintViolationWithTemplate(
              "Password must contain at least one lowercase letter")
          .addConstraintViolation();
      valid = false;
    }

    if (!password.chars().anyMatch(Character::isDigit)) {
      context
          .buildConstraintViolationWithTemplate("Password must contain at least one digit")
          .addConstraintViolation();
      valid = false;
    }

    if (password.chars().allMatch(c -> Character.isLetterOrDigit(c))) {
      context
          .buildConstraintViolationWithTemplate(
              "Password must contain at least one special character")
          .addConstraintViolation();
      valid = false;
    }

    return valid;
  }
}
