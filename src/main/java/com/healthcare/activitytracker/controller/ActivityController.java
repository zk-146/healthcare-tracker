package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.ActivityRequest;
import com.healthcare.activitytracker.model.dto.ActivityResponse;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Activities", description = "Create, read, update and delete activity records")
@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

  private final ActivityService activityService;

  public ActivityController(ActivityService activityService) {
    this.activityService = activityService;
  }

  /** Logs a new activity for the authenticated user. Returns 201 with the created resource. */
  @Operation(summary = "Log a new activity")
  @PostMapping
  public ResponseEntity<ActivityResponse> create(
      Authentication auth, @Valid @RequestBody ActivityRequest request) {
    UUID userId = (UUID) auth.getPrincipal();
    ActivityResponse response = activityService.createActivity(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** Returns a paginated list of activities for the authenticated user with optional filters. */
  @Operation(summary = "List activities with optional filters and pagination")
  @GetMapping
  public ResponseEntity<Page<ActivityResponse>> list(
      Authentication auth,
      @RequestParam(required = false) ActivityType activityType,
      @RequestParam(required = false) ActivitySource source,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @PageableDefault(size = 20) Pageable pageable) {
    UUID userId = (UUID) auth.getPrincipal();
    Page<ActivityResponse> page =
        activityService.getActivities(userId, activityType, source, from, to, pageable);
    return ResponseEntity.ok(page);
  }

  /**
   * Returns a single activity by ID, scoped to the authenticated user. Returns 404 if not found.
   */
  @Operation(summary = "Get a single activity by ID")
  @GetMapping("/{id}")
  public ResponseEntity<ActivityResponse> get(Authentication auth, @PathVariable UUID id) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(activityService.getActivity(userId, id));
  }

  /** Replaces all fields of an existing activity. Returns 404 if not found. */
  @Operation(summary = "Update an existing activity")
  @PutMapping("/{id}")
  public ResponseEntity<ActivityResponse> update(
      Authentication auth, @PathVariable UUID id, @Valid @RequestBody ActivityRequest request) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(activityService.updateActivity(userId, id, request));
  }

  /** Permanently deletes an activity. Returns 204 on success, 404 if not found. */
  @Operation(summary = "Delete an activity")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
    UUID userId = (UUID) auth.getPrincipal();
    activityService.deleteActivity(userId, id);
    return ResponseEntity.noContent().build();
  }
}
