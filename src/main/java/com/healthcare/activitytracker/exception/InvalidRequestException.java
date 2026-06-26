package com.healthcare.activitytracker.exception;

/**
 * Thrown for invalid client input where the message is intentionally safe to return to the caller
 * (e.g. an out-of-range date range). Distinct from a raw {@link IllegalArgumentException}, whose
 * message may originate from library internals and must not be exposed.
 */
public class InvalidRequestException extends RuntimeException {
  public InvalidRequestException(String message) {
    super(message);
  }
}
