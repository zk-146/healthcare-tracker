package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityResponse {
  private UUID id;
  private ActivityType activityType;
  private ActivitySource source;
  private String deviceId;
  private LocalDateTime startedAt;
  private LocalDateTime endedAt;
  private Integer durationMinutes;
  private Double distanceKm;
  private Double caloriesBurned;
  private Integer heartRateAvg;
  private Integer steps;
  private String notes;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
