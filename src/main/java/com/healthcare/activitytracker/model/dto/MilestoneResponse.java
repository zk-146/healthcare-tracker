package com.healthcare.activitytracker.model.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single earned streak milestone. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MilestoneResponse {

  /** The streak length in days that was reached (3, 7, 14, 30, 60, 100, or 365). */
  private Integer milestoneDays;

  private LocalDateTime achievedAt;
}
