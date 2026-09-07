package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.dto.DigestResponse;
import com.healthcare.activitytracker.model.dto.SummaryResponse;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Generates a short natural-language digest of a user's recent activity by feeding {@link
 * SummaryService} aggregates to the local LLM.
 *
 * <p>The prompt contains only aggregate statistics — never the user's name, email, or other PII.
 * When Ollama is unavailable the response carries {@code available=false} and a static fallback
 * message instead of erroring out.
 */
@Service
public class ActivityDigestService {

  static final String UNAVAILABLE_MESSAGE =
      "AI insights are temporarily unavailable. Your activity data is unaffected — "
          + "please try again later.";

  static final String EMPTY_PERIOD_MESSAGE =
      "No activities recorded in this period yet — log a workout to get your first digest!";

  private final SummaryService summaryService;
  private final OllamaClient ollamaClient;

  public ActivityDigestService(SummaryService summaryService, OllamaClient ollamaClient) {
    this.summaryService = summaryService;
    this.ollamaClient = ollamaClient;
  }

  /**
   * Builds an AI digest for the given period.
   *
   * @param userId the authenticated user's ID
   * @param period one of {@code daily}, {@code weekly}, {@code monthly} (case-insensitive)
   * @param zone the user's timezone
   * @throws IllegalArgumentException for an unknown period (mapped to HTTP 400)
   */
  public DigestResponse generateDigest(UUID userId, String period, ZoneId zone) {
    String normalized = period == null ? "weekly" : period.toLowerCase(Locale.ROOT);
    SummaryResponse summary =
        switch (normalized) {
          case "daily" -> summaryService.getDailySummary(userId, zone);
          case "weekly" -> summaryService.getWeeklySummary(userId, zone);
          case "monthly" -> summaryService.getMonthlySummary(userId, zone);
          default ->
              throw new IllegalArgumentException("period must be one of: daily, weekly, monthly");
        };

    if (summary.getTotalActivities() == 0) {
      return response(normalized, summary, true, EMPTY_PERIOD_MESSAGE);
    }

    Optional<String> digest = ollamaClient.generate(buildPrompt(normalized, summary));
    return response(normalized, summary, digest.isPresent(), digest.orElse(UNAVAILABLE_MESSAGE));
  }

  private static DigestResponse response(
      String period, SummaryResponse summary, boolean available, String digest) {
    return DigestResponse.builder()
        .period(period)
        .from(summary.getFrom())
        .to(summary.getTo())
        .available(available)
        .digest(digest)
        .build();
  }

  private static String buildPrompt(String period, SummaryResponse summary) {
    StringBuilder sb = new StringBuilder();
    sb.append("You are a friendly fitness coach for an activity-tracking app. ")
        .append("Write a short, encouraging ")
        .append(period)
        .append(" digest (max 120 words, plain text, no headings) based ONLY on these stats. ")
        .append("Do not invent numbers.\n\n");
    sb.append("Period: ").append(summary.getFrom()).append(" to ").append(summary.getTo());
    sb.append("\nTotal activities: ").append(summary.getTotalActivities());
    sb.append("\nTotal duration: ").append(summary.getTotalDurationMinutes()).append(" minutes");
    sb.append("\nTotal calories: ").append(Math.round(summary.getTotalCaloriesBurned()));
    sb.append("\nTotal distance: ").append(summary.getTotalDistanceKm()).append(" km");
    sb.append("\nTotal steps: ").append(summary.getTotalSteps());
    sb.append("\nCurrent streak: ").append(summary.getStreakDays()).append(" days");
    if (summary.getByActivityType() != null && !summary.getByActivityType().isEmpty()) {
      sb.append("\nBreakdown by activity:");
      summary.getByActivityType().stream()
          .limit(5)
          .forEach(
              t ->
                  sb.append("\n- ")
                      .append(t.getType())
                      .append(": ")
                      .append(t.getCount())
                      .append(" sessions, ")
                      .append(t.getTotalMinutes())
                      .append(" min"));
    }
    return sb.toString();
  }
}
