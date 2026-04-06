package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.model.enums.GoalMetric;
import java.time.LocalDate;
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
public class GoalResponse {

  private UUID id;
  private GoalMetric metric;
  private double targetValue;
  private boolean active;
  private LocalDate lastAchievedDate;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
