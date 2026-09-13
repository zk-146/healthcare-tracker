package com.healthcare.activitytracker.model.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a manually triggered Google Health sync. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoogleHealthSyncResponse {

  /** Activities newly imported by this run. Zero is a normal result, not a failure. */
  private int imported;

  /** The watermark after this run — the latest workout start time seen. */
  private LocalDateTime lastSyncedAt;
}
