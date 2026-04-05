package com.healthcare.activitytracker.controller;

import com.healthcare.activitytracker.model.dto.SummaryResponse;
import com.healthcare.activitytracker.service.SummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Summary", description = "Aggregated activity summaries and streak tracking")
@RestController
@RequestMapping("/api/v1/summary")
public class SummaryController {

  private static final Logger log = LoggerFactory.getLogger(SummaryController.class);

  private final SummaryService summaryService;

  public SummaryController(SummaryService summaryService) {
    this.summaryService = summaryService;
  }

  @Operation(summary = "Get activity summary for a custom date range")
  @GetMapping
  public ResponseEntity<SummaryResponse> getSummary(
      Authentication auth,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    ZoneId zone = resolveZone(timezone);
    return ResponseEntity.ok(summaryService.getSummary(userId, from, to, zone));
  }

  @Operation(summary = "Get today's activity summary")
  @GetMapping("/daily")
  public ResponseEntity<SummaryResponse> getDaily(
      Authentication auth,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(summaryService.getDailySummary(userId, resolveZone(timezone)));
  }

  @Operation(summary = "Get week-to-date activity summary")
  @GetMapping("/weekly")
  public ResponseEntity<SummaryResponse> getWeekly(
      Authentication auth,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(summaryService.getWeeklySummary(userId, resolveZone(timezone)));
  }

  @Operation(summary = "Get month-to-date activity summary")
  @GetMapping("/monthly")
  public ResponseEntity<SummaryResponse> getMonthly(
      Authentication auth,
      @RequestHeader(value = "X-User-Timezone", required = false) String timezone) {
    UUID userId = (UUID) auth.getPrincipal();
    return ResponseEntity.ok(summaryService.getMonthlySummary(userId, resolveZone(timezone)));
  }

  /**
   * Parses the timezone string from the X-User-Timezone header. Falls back to UTC on null, blank,
   * or unrecognized values.
   */
  private ZoneId resolveZone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(timezone);
    } catch (java.time.DateTimeException e) {
      log.warn("Unrecognized timezone '{}', defaulting to UTC", timezone);
      return ZoneOffset.UTC;
    }
  }
}
