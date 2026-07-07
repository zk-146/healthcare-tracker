package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.model.enums.ConnectionStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** Reports whether the owner's Google Health (Fitbit) integration is linked and healthy. */
@Getter
@Builder
public class GoogleHealthStatusResponse {

  /** True if a connection row exists (regardless of whether it currently needs reconnecting). */
  private final boolean connected;

  /** CONNECTED or NEEDS_RECONNECT; null when never linked. */
  private final ConnectionStatus status;

  /** Watermark of the most recent successful sync; null if not yet synced. */
  private final LocalDateTime lastSyncedAt;
}
