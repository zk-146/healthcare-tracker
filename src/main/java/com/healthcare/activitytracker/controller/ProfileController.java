package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.ProfileResponse;
import com.healthcare.activitytracker.model.dto.ProfileUpdateRequest;
import com.healthcare.activitytracker.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Profile", description = "View and update the authenticated user's profile")
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
    this.profileService = profileService;
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
}
