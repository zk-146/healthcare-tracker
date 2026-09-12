package com.healthcare.activitytracker.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StrongPasswordValidatorTest {

  private final StrongPasswordValidator validator = new StrongPasswordValidator();
  private ConstraintValidatorContext context;

  @BeforeEach
  void setUp() {
    context = mock(ConstraintValidatorContext.class);
    ConstraintValidatorContext.ConstraintViolationBuilder builder =
        mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
    when(context.buildConstraintViolationWithTemplate(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(builder);
  }

  @Test
  void isValid_returnsTrueForNullPassword() {
    assertThat(validator.isValid(null, context)).isTrue();
  }

  @Test
  void isValid_returnsTrueForBlankPassword() {
    assertThat(validator.isValid("   ", context)).isTrue();
  }

  @Test
  void isValid_returnsTrueForCompliantPassword() {
    assertThat(validator.isValid("Str0ng!Passw0rd", context)).isTrue();
  }

  @Test
  void isValid_returnsFalseWhenTooShort() {
    assertThat(validator.isValid("Sh0rt!Pw", context)).isFalse();
  }

  @Test
  void isValid_returnsFalseWhenTooLong() {
    String tooLong = "Aa1!" + "a".repeat(126);
    assertThat(validator.isValid(tooLong, context)).isFalse();
  }

  @Test
  void isValid_returnsFalseWithoutUppercase() {
    assertThat(validator.isValid("weak!password1", context)).isFalse();
  }

  @Test
  void isValid_returnsFalseWithoutLowercase() {
    assertThat(validator.isValid("WEAK!PASSWORD1", context)).isFalse();
  }

  @Test
  void isValid_returnsFalseWithoutDigit() {
    assertThat(validator.isValid("Weak!Password", context)).isFalse();
  }

  @Test
  void isValid_returnsFalseWithoutSpecialCharacter() {
    assertThat(validator.isValid("Weak1Password123", context)).isFalse();
  }

  @Test
  void isValid_atExactMinAndMaxLengthBoundariesIsTrue() {
    assertThat(validator.isValid("Aa1!Aa1!Aa1!", context)).isTrue(); // exactly 12 chars
    String exactly128 = "Aa1!" + "a".repeat(124);
    assertThat(validator.isValid(exactly128, context)).isTrue();
  }
}
