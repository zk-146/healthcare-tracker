package com.healthcare.activitytracker.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleNotFound_returns404WithMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleNotFound(new ResourceNotFoundException("activity not found"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("error", "activity not found");
    assertThat(response.getBody()).containsKey("timestamp");
  }

  @Test
  void handleUnauthorized_returns401WithMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleUnauthorized(new UnauthorizedException("bad token"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).containsEntry("error", "bad token");
  }

  @Test
  void handleConflict_returns409WithMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleConflict(new ResourceConflictException("already exists"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("error", "already exists");
  }

  @Test
  void handleValidation_collectsFieldAndGlobalErrors() {
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError = new FieldError("activityRequest", "distanceKm", "must be positive");
    ObjectError globalError = new ObjectError("activityRequest", "start must be before end");
    when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));
    when(bindingResult.getGlobalErrors()).thenReturn(java.util.List.of(globalError));

    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    when(ex.getBindingResult()).thenReturn(bindingResult);

    ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "Validation failed");
    @SuppressWarnings("unchecked")
    Map<String, String> details = (Map<String, String>) response.getBody().get("details");
    assertThat(details).containsEntry("distanceKm", "must be positive");
    assertThat(details).containsEntry("activityRequest", "start must be before end");
  }

  @Test
  void handleFieldValidation_returnsSingleFieldDetail() {
    FieldValidationException ex =
        new FieldValidationException("startedAt", "must be within the last 24 hours");

    ResponseEntity<Map<String, Object>> response = handler.handleFieldValidation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    @SuppressWarnings("unchecked")
    Map<String, String> details = (Map<String, String>) response.getBody().get("details");
    assertThat(details).containsEntry("startedAt", "must be within the last 24 hours");
  }

  @Test
  void handleCsvImport_returns400WithMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleCsvImport(new CsvImportException("missing required column: date"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "missing required column: date");
  }

  @Test
  void handleMaxUploadSize_returns413WithGenericMessage() {
    MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024);

    ResponseEntity<Map<String, Object>> response = handler.handleMaxUploadSize(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(response.getBody())
        .containsEntry("error", "Uploaded file exceeds the maximum allowed size");
  }

  @Test
  void handleMissingParam_includesParameterName() {
    MissingServletRequestParameterException ex =
        new MissingServletRequestParameterException("from", "LocalDate");

    ResponseEntity<Map<String, Object>> response = handler.handleMissingParam(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "Required parameter 'from' is missing");
  }

  @Test
  void handleIllegalArgument_returns400WithMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleIllegalArgument(new IllegalArgumentException("unsupported enum value"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "unsupported enum value");
  }

  @Test
  void handleDataIntegrity_returns409WithGenericMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleDataIntegrity(new DataIntegrityViolationException("duplicate key"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("error", "Data integrity violation");
  }

  @Test
  void handleOptimisticLocking_returns409WithFriendlyMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleOptimisticLocking(new OptimisticLockingFailureException("stale row"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody())
        .containsEntry(
            "error", "This record was modified by another request. Please refresh and try again.");
  }

  @Test
  void handleMessageNotReadable_returns400WithGenericMessage() {
    HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
    when(ex.getMessage()).thenReturn("JSON parse error");

    ResponseEntity<Map<String, Object>> response = handler.handleMessageNotReadable(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "Malformed request body");
  }

  @Test
  void handleTypeMismatch_includesParamNameAndExpectedType() throws Exception {
    MethodParameter param =
        new MethodParameter(
            GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", Integer.class), 0);
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException(
            "abc", Integer.class, "limit", param, new TypeMismatchException("abc", Integer.class));

    ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .containsEntry("error", "Parameter 'limit' has an invalid value; expected type Integer");
  }

  @Test
  void handleTypeMismatch_omitsExpectedTypeWhenUnknown() throws Exception {
    MethodParameter param =
        new MethodParameter(
            GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", Integer.class), 0);
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException(
            "abc", null, "limit", param, new TypeMismatchException("abc", Integer.class));

    ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);

    assertThat(response.getBody()).containsEntry("error", "Parameter 'limit' has an invalid value");
  }

  @SuppressWarnings("unused")
  private static void dummyTarget(Integer limit) {}

  @Test
  void handleResponseStatus_returnsTheExceptionsOwnStatusAndReason() {
    ResponseStatusException ex =
        new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "Google Health integration is disabled");

    ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).containsEntry("error", "Google Health integration is disabled");
  }

  @Test
  void handleResponseStatus_fallsBackTo500ForAnUnresolvableStatusCode() {
    ResponseStatusException ex =
        new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(599), "odd");

    ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void handleNoResourceFound_returns404() {
    NoResourceFoundException ex = mock(NoResourceFoundException.class);

    ResponseEntity<Map<String, Object>> response = handler.handleNoResourceFound(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("error", "Not found");
  }

  @Test
  void handleGeneral_returns500WithGenericMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleGeneral(new RuntimeException("boom"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred");
  }
}
