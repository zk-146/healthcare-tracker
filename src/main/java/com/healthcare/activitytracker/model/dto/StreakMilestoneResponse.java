package com.healthcare.activitytracker.model.dto;

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
public class StreakMilestoneResponse {
  private UUID id;
  private Integer milestoneDays;
  private LocalDateTime achievedAt;
  private UUID triggeringActivityId;
}
