package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.GoalRequest;
import com.healthcare.activitytracker.model.dto.GoalResponse;
import com.healthcare.activitytracker.model.dto.GoalUpdateRequest;
import com.healthcare.activitytracker.service.GoalService;
import com.healthcare.activitytracker.util.TimezoneResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Goals", description = "User-defined activity targets and progress against them")
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

  private final GoalService goalService;

  public GoalController(GoalService goalService) {
    this.goalService = goalService;
  }

  /**
   * Lists the authenticated user's live goals with current progress. The optional {@code
   * X-User-Timezone} header decides where each period begins; defaults to UTC if absent or
   * unrecognized.
   */
  @Operation(summary = "List active goals with progress")
  @GetMapping
  public ResponseEntity<List<GoalResponse>> list(
      Authentication auth,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(goalService.getGoals(userId, TimezoneResolver.resolveZone(timezone)));
  }

  /** Creates a goal. Conflicts (409) if one already covers this metric and period. */
  @Operation(summary = "Create a goal")
  @PostMapping
  public ResponseEntity<GoalResponse> create(
      Authentication auth,
      @Valid @RequestBody GoalRequest request,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    GoalResponse created =
        goalService.createGoal(userId, request, TimezoneResolver.resolveZone(timezone));
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Changes a goal's target. Metric and period are immutable; retire and recreate to change them.
   */
  @Operation(summary = "Update a goal's target")
  @PutMapping("/{id}")
  public ResponseEntity<GoalResponse> update(
      Authentication auth,
      @PathVariable UUID id,
      @Valid @RequestBody GoalUpdateRequest request,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(
        goalService.updateGoal(userId, id, request, TimezoneResolver.resolveZone(timezone)));
  }

  /** Retires a goal, freeing its metric/period slot for a new one. */
  @Operation(summary = "Retire a goal")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
    UUID userId = (UUID) auth.getPrincipal();
    goalService.deleteGoal(userId, id);
    return ResponseEntity.noContent().build();
  }
}
