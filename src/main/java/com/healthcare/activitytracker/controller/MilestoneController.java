package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.MilestoneProgressResponse;
import com.healthcare.activitytracker.model.dto.MilestoneResponse;
import com.healthcare.activitytracker.service.MilestoneService;
import com.healthcare.activitytracker.util.TimezoneResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Milestones", description = "Streak milestones the authenticated user has earned")
@RestController
@RequestMapping("/api/v1/milestones")
public class MilestoneController {

  private final MilestoneService milestoneService;

  public MilestoneController(MilestoneService milestoneService) {
    this.milestoneService = milestoneService;
  }

  /** Returns every streak milestone the authenticated user has earned, longest streak first. */
  @Operation(summary = "List earned streak milestones")
  @GetMapping
  public ResponseEntity<List<MilestoneResponse>> list(Authentication auth) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(milestoneService.getMilestones(userId));
  }

  /**
   * Progress toward the next unearned milestone. The optional {@code X-User-Timezone} header (e.g.
   * {@code America/New_York}) is used for streak boundary calculations; defaults to UTC if absent
   * or unrecognized.
   */
  @Operation(summary = "Progress toward the next streak milestone")
  @GetMapping("/progress")
  public ResponseEntity<MilestoneProgressResponse> progress(
      Authentication auth,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(
        milestoneService.getProgress(userId, TimezoneResolver.resolveZone(timezone)));
  }
}
