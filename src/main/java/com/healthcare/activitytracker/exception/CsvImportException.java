package com.healthcare.activitytracker.exception;

/** Thrown when an uploaded CSV file cannot be imported (missing/unreadable, wrong columns). */
public class CsvImportException extends RuntimeException {
  public CsvImportException(String message) {
    super(message);
  }
}
