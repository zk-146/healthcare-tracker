package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.AuthResponse;
import com.healthcare.activitytracker.model.dto.ForgotPasswordRequest;
import com.healthcare.activitytracker.model.dto.LoginRequest;
import com.healthcare.activitytracker.model.dto.RefreshRequest;
import com.healthcare.activitytracker.model.dto.RegisterRequest;
import com.healthcare.activitytracker.model.dto.ResendVerificationRequest;
import com.healthcare.activitytracker.model.dto.ResetPasswordRequest;
import com.healthcare.activitytracker.model.dto.VerifyEmailRequest;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.EmailVerificationService;
import com.healthcare.activitytracker.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Register, login, token refresh, logout, and recovery")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthService authService;
  private final EmailVerificationService emailVerificationService;
  private final PasswordResetService passwordResetService;

  public AuthController(
      AuthService authService,
      EmailVerificationService emailVerificationService,
      PasswordResetService passwordResetService) {
    this.authService = authService;
    this.emailVerificationService = emailVerificationService;
    this.passwordResetService = passwordResetService;
  }

  /** Registers a new user and returns access and refresh tokens. Returns 201 on success. */
  @Operation(summary = "Register a new user account")
  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    AuthResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** Authenticates a user and returns access and refresh tokens. */
  @Operation(summary = "Authenticate and receive access + refresh tokens")
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(response);
  }

  /** Exchanges a valid refresh token for a new access token. The refresh token is rotated. */
  @Operation(summary = "Exchange a refresh token for a new access token")
  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    AuthResponse response = authService.refresh(request.getRefreshToken());
    return ResponseEntity.ok(response);
  }

  /**
   * Revokes the caller's current access token. Returns 204 regardless of whether the token was
   * already invalid (idempotent).
   */
  @Operation(summary = "Revoke the current access token (logout)")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
      String token = authHeader.substring(BEARER_PREFIX.length());
      authService.logout(token);
    }
    return ResponseEntity.noContent().build();
  }

  /** Confirms ownership of an email address using a verification token. Returns 204. */
  @Operation(summary = "Verify an email address")
  @PostMapping("/verify-email")
  public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    emailVerificationService.verify(request.getToken());
    return ResponseEntity.noContent().build();
  }

  /**
   * Re-sends a verification email if the account exists and is unverified. Always returns 204 to
   * avoid revealing whether an email is registered.
   */
  @Operation(summary = "Resend the email-verification token")
  @PostMapping("/resend-verification")
  public ResponseEntity<Void> resendVerification(
      @Valid @RequestBody ResendVerificationRequest request) {
    emailVerificationService.resend(request.getEmail());
    return ResponseEntity.noContent().build();
  }

  /**
   * Initiates a password reset. Always returns 204 to avoid revealing whether an email is
   * registered.
   */
  @Operation(summary = "Request a password-reset token")
  @PostMapping("/forgot-password")
  public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    passwordResetService.requestReset(request.getEmail());
    return ResponseEntity.noContent().build();
  }

  /** Completes a password reset using a valid token. Returns 204 on success. */
  @Operation(summary = "Reset a password using a reset token")
  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    passwordResetService.reset(request.getToken(), request.getNewPassword());
    return ResponseEntity.noContent().build();
  }
}
