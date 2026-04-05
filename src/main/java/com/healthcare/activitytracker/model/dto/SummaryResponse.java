package com.healthcare.activitytracker.model.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SummaryResponse {

  private LocalDate from;
  private LocalDate to;
  private long totalActivities;
  private long totalDurationMinutes;
  private double totalCaloriesBurned;
  private double totalDistanceKm;
  private long totalSteps;
  private int streakDays;
  private double averageDailyCalories;
  private Map<String, Long> bySource;
  private List<ActivityTypeSummary> byActivityType;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ActivityTypeSummary {
    private String type;
    private String source;
    private long count;
    private long totalMinutes;
    private double totalCalories;
  }
}
