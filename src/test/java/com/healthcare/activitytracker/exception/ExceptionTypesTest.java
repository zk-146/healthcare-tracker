package com.healthcare.activitytracker.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The custom exception types are one-line message carriers with no branching logic of their own;
 * {@link GlobalExceptionHandlerTest} already exercises how each is mapped to a response. This just
 * confirms the constructors wire the message (and, for {@link FieldValidationException}, the field
 * name) through correctly.
 */
class ExceptionTypesTest {

  @Test
  void resourceNotFoundException_carriesMessage() {
    assertThat(new ResourceNotFoundException("activity not found"))
        .hasMessage("activity not found")
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void resourceConflictException_carriesMessage() {
    assertThat(new ResourceConflictException("already exists"))
        .hasMessage("already exists")
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void unauthorizedException_carriesMessage() {
    assertThat(new UnauthorizedException("bad token"))
        .hasMessage("bad token")
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void csvImportException_carriesMessage() {
    assertThat(new CsvImportException("missing required column: date"))
        .hasMessage("missing required column: date")
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void fieldValidationException_carriesFieldAndMessage() {
    FieldValidationException ex =
        new FieldValidationException("startedAt", "must be within the last 24 hours");

    assertThat(ex).hasMessage("must be within the last 24 hours");
    assertThat(ex.getField()).isEqualTo("startedAt");
  }
}
