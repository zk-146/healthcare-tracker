package com.healthcare.activitytracker.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
    log.warn("Resource not found: {}", ex.getMessage());
    return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
    log.warn("Unauthorized: {}", ex.getMessage());
    return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(ResourceConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(ResourceConflictException ex) {
    log.warn("Conflict: {}", ex.getMessage());
    return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(error.getField(), error.getDefaultMessage());
    }
    // Capture class-level constraint violations
    ex.getBindingResult()
        .getGlobalErrors()
        .forEach(error -> fieldErrors.put(error.getObjectName(), error.getDefaultMessage()));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", "Validation failed");
    body.put("details", fieldErrors);
    body.put("timestamp", Instant.now().toString());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> handleMissingParam(
      MissingServletRequestParameterException ex) {
    log.warn("Missing required parameter: {}", ex.getMessage());
    return buildResponse(
        HttpStatus.BAD_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing");
  }

  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<Map<String, Object>> handleInvalidRequest(InvalidRequestException ex) {
    // Message is intentionally client-safe.
    log.warn("Invalid request: {}", ex.getMessage());
    return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    // The message may originate from library internals — log it, but do not leak it to the client.
    log.warn("Illegal argument: {}", ex.getMessage());
    return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleDataIntegrity(
      DataIntegrityViolationException ex) {
    log.error("Data integrity violation", ex);
    return buildResponse(HttpStatus.CONFLICT, "Data integrity violation");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, Object>> handleOptimisticLocking(
      OptimisticLockingFailureException ex) {
    log.warn("Concurrent modification detected: {}", ex.getMessage());
    return buildResponse(
        HttpStatus.CONFLICT,
        "This record was modified by another request. Please refresh and try again.");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleMessageNotReadable(
      HttpMessageNotReadableException ex) {
    log.warn("Malformed request body: {}", ex.getMessage());
    return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
    log.error("Unhandled exception", ex);
    return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }

  private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", message);
    body.put("details", null);
    body.put("timestamp", Instant.now().toString());
    return ResponseEntity.status(status).body(body);
  }
}
