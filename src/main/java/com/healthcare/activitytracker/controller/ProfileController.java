package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.ProfileResponse;
import com.healthcare.activitytracker.model.dto.ProfileUpdateRequest;
import com.healthcare.activitytracker.model.dto.UserDataExportResponse;
import com.healthcare.activitytracker.service.AccountService;
import com.healthcare.activitytracker.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(
    name = "Profile",
    description =
        "View and update the authenticated user's profile, export data, or delete account")
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

  private final ProfileService profileService;
  private final AccountService accountService;

  public ProfileController(ProfileService profileService, AccountService accountService) {
    this.profileService = profileService;
    this.accountService = accountService;
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
   * Exports a complete, portable copy of the authenticated user's data (profile, activities, and
   * streak milestones) for GDPR data-portability requests.
   */
  @Operation(summary = "Export all of the current user's data")
  @GetMapping("/export")
  public ResponseEntity<UserDataExportResponse> exportData(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(accountService.exportUserData(userId));
  }

  /**
   * Permanently deletes the authenticated user and all associated data (right to be forgotten).
   * Returns 204 on success.
   */
  @Operation(summary = "Permanently delete the current user's account and data")
  @DeleteMapping
  public ResponseEntity<Void> deleteAccount(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    accountService.deleteAccount(userId);
    return ResponseEntity.noContent().build();
  }
}
