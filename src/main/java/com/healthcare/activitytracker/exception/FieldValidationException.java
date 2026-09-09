package com.healthcare.activitytracker.exception;

/**
 * A single-field validation failure discovered outside Jakarta Bean Validation — typically because
 * the rule needs request context Bean Validation cannot see, such as the caller's own timezone.
 * {@link GlobalExceptionHandler} maps this to the same {@code details: {field: message}} response
 * shape {@link org.springframework.web.bind.MethodArgumentNotValidException} produces, so API
 * consumers see one consistent 400 contract regardless of which layer rejected the request.
 */
public class FieldValidationException extends RuntimeException {

  private final String field;

  public FieldValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
