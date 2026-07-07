package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.AuthResponse;
import com.healthcare.activitytracker.model.dto.LoginRequest;
import com.healthcare.activitytracker.model.dto.PasswordChangeRequest;
import com.healthcare.activitytracker.model.dto.RefreshRequest;
import com.healthcare.activitytracker.model.dto.RegisterRequest;
import com.healthcare.activitytracker.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Register, login, token refresh and logout")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
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
   * Changes the authenticated user's password. All refresh tokens are revoked, so other sessions
   * must log in again with the new password.
   */
  @Operation(summary = "Change the current user's password (revokes all refresh tokens)")
  @PostMapping("/change-password")
  public ResponseEntity<Void> changePassword(
      Authentication auth, @Valid @RequestBody PasswordChangeRequest request) {
    UUID userId = (UUID) auth.getPrincipal();
    authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
    return ResponseEntity.noContent().build();
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
}
