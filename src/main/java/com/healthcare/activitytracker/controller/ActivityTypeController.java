package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.ActivityTypeResponse;
import com.healthcare.activitytracker.model.enums.ActivityType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Static metadata about the selectable activity types. A top-level resource rather than {@code
 * /activities/types}: it is not a sub-resource of the activities collection, and keeping it away
 * from {@code GET /activities/{id}} avoids reasoning about literal-vs-templated path precedence.
 */
@Tag(name = "Activity Types", description = "The selectable activity types and their MET values")
@RestController
@RequestMapping("/api/v1/activity-types")
public class ActivityTypeController {

  /** Derived from the enum at request time — no state, no repository. */
  @Operation(summary = "List the selectable activity types")
  @GetMapping
  public ResponseEntity<List<ActivityTypeResponse>> list() {
    return ResponseEntity.ok(
        Arrays.stream(ActivityType.values()).map(ActivityTypeController::toResponse).toList());
  }

  private static ActivityTypeResponse toResponse(ActivityType type) {
    return ActivityTypeResponse.builder()
        .name(type.name())
        .label(toLabel(type.name()))
        .metValue(type.getMetValue())
        .build();
  }

  /** {@code STRENGTH_TRAINING} becomes {@code Strength Training}. */
  private static String toLabel(String enumName) {
    return Arrays.stream(enumName.split("_"))
        .map(word -> word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT))
        .collect(Collectors.joining(" "));
  }
}
