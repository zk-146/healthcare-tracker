package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.ProfileResponse;
import com.healthcare.activitytracker.model.dto.ProfileUpdateRequest;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Profile", description = "View, update and delete the authenticated user's profile")
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final ProfileService profileService;
  private final AuthService authService;

  public ProfileController(ProfileService profileService, AuthService authService) {
    this.profileService = profileService;
    this.authService = authService;
  }

  /** Returns the authenticated user's profile. */
  @Operation(summary = "Get the current user's profile")
  @GetMapping
  public ResponseEntity<ProfileResponse> getProfile(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(profileService.getProfile(userId));
  }

  /** Partially updates the authenticated user's profile. Null fields in the request are ignored. */
  @Operation(summary = "Update the current user's profile")
  @PutMapping
  public ResponseEntity<ProfileResponse> updateProfile(
      Authentication auth, @Valid @RequestBody ProfileUpdateRequest request) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(profileService.updateProfile(userId, request));
  }

  /**
   * Permanently deletes the authenticated user's account and all associated data (activities,
   * refresh tokens, streak milestones). The current access token is revoked. Irreversible.
   */
  @Operation(summary = "Delete the current user's account and all associated data")
  @DeleteMapping
  public ResponseEntity<Void> deleteAccount(
      Authentication auth,
      @RequestHeader(value = "Authorization", required = false) String authHeader) {
    UUID userId = (UUID) auth.getPrincipal();
    // Blacklist the current access token first so it cannot be used after deletion
    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
      authService.logout(authHeader.substring(BEARER_PREFIX.length()));
    }
    profileService.deleteAccount(userId);
    return ResponseEntity.noContent().build();
  }
}
