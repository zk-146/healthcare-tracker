package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.GoalRequest;
import com.healthcare.activitytracker.model.dto.GoalResponse;
import com.healthcare.activitytracker.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for managing user-defined daily fitness goals.
 *
 * <p>All endpoints are scoped to the authenticated user. Goals are evaluated automatically after
 * each activity is recorded.
 */
@Tag(name = "Goals", description = "Create and manage daily fitness goals")
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

  private final GoalService goalService;

  public GoalController(GoalService goalService) {
    this.goalService = goalService;
  }

  /** Creates a new daily fitness goal for the authenticated user. */
  @Operation(summary = "Create a goal")
  @PostMapping
  public ResponseEntity<GoalResponse> create(
      Authentication auth, @Valid @RequestBody GoalRequest request) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(userId, request));
  }

  /** Returns all active goals for the authenticated user. */
  @Operation(summary = "List active goals")
  @GetMapping
  public ResponseEntity<List<GoalResponse>> listActive(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(goalService.listActive(userId));
  }

  /** Returns a single goal by ID. Returns 404 if it does not belong to the user. */
  @Operation(summary = "Get a goal by ID")
  @GetMapping("/{id}")
  public ResponseEntity<GoalResponse> getById(Authentication auth, @PathVariable UUID id) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(goalService.getById(userId, id));
  }

  /** Updates the target value of an existing goal. Returns 404 if it does not belong to the user. */
  @Operation(summary = "Update a goal's target value")
  @PutMapping("/{id}")
  public ResponseEntity<GoalResponse> update(
      Authentication auth, @PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(goalService.update(userId, id, request));
  }

  /** Soft-deletes a goal (sets active = false). Returns 404 if it does not belong to the user. */
  @Operation(summary = "Deactivate a goal")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(Authentication auth, @PathVariable UUID id) {
    UUID userId = (UUID) auth.getPrincipal();
    goalService.deactivate(userId, id);
    return ResponseEntity.noContent().build();
  }
}
