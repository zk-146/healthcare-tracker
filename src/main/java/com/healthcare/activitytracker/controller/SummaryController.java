package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.DigestResponse;
import com.healthcare.activitytracker.model.dto.SummaryResponse;
import com.healthcare.activitytracker.service.ActivityDigestService;
import com.healthcare.activitytracker.service.SummaryService;
import com.healthcare.activitytracker.util.TimezoneResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Summary", description = "Aggregated activity summaries and streak tracking")
@RestController
@RequestMapping("/api/v1/summary")
public class SummaryController {

  private final SummaryService summaryService;
  private final ActivityDigestService activityDigestService;

  public SummaryController(
      SummaryService summaryService, ActivityDigestService activityDigestService) {
    this.summaryService = summaryService;
    this.activityDigestService = activityDigestService;
  }

  /**
   * Returns aggregated activity statistics for the specified date range. The optional {@code
   * X-User-Timezone} header (e.g. {@code America/New_York}) is used for streak boundary
   * calculations; defaults to UTC if absent or unrecognized.
   */
  @Operation(summary = "Get activity summary for a custom date range")
  @GetMapping
  public ResponseEntity<SummaryResponse> getSummary(
      Authentication auth,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    ZoneId zone = TimezoneResolver.resolveZone(timezone);
    return ResponseEntity.ok(summaryService.getSummary(userId, from, to, zone));
  }

  /** Returns today's activity summary in the user's local timezone. */
  @Operation(summary = "Get today's activity summary")
  @GetMapping("/daily")
  public ResponseEntity<SummaryResponse> getDaily(
      Authentication auth,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(
        summaryService.getDailySummary(userId, TimezoneResolver.resolveZone(timezone)));
  }

  /**
   * Returns a week-to-date activity summary (Monday through today) in the user's local timezone.
   */
  @Operation(summary = "Get week-to-date activity summary")
  @GetMapping("/weekly")
  public ResponseEntity<SummaryResponse> getWeekly(
      Authentication auth,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(
        summaryService.getWeeklySummary(userId, TimezoneResolver.resolveZone(timezone)));
  }

  /** Returns a month-to-date activity summary (1st through today) in the user's local timezone. */
  @Operation(summary = "Get month-to-date activity summary")
  @GetMapping("/monthly")
  public ResponseEntity<SummaryResponse> getMonthly(
      Authentication auth,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(
        summaryService.getMonthlySummary(userId, TimezoneResolver.resolveZone(timezone)));
  }

  /**
   * Returns an AI-generated natural-language digest of the user's recent activity. When the local
   * LLM is unavailable the response has {@code available=false} and a fallback message — never an
   * error.
   */
  @Operation(summary = "Get an AI-generated activity digest (daily, weekly, or monthly)")
  @GetMapping("/digest")
  public ResponseEntity<DigestResponse> getDigest(
      Authentication auth,
      @RequestParam(defaultValue = "weekly") String period,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(
        activityDigestService.generateDigest(
            userId, period, TimezoneResolver.resolveZone(timezone)));
  }
}
