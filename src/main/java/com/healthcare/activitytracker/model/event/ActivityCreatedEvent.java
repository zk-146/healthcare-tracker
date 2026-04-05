package com.healthcare.activitytracker.model.event;

import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event published to Kafka whenever a new activity is persisted.
 *
 * <p>Lean payload — contains only the fields a downstream consumer (analytics, notifications,
 * leaderboards, daily-summary cache) needs. Not a full {@code ActivityResponse} dump.
 *
 * <p>{@code eventId} is a per-publish UUID that downstream consumers can use as an idempotency key
 * to de-duplicate on retry.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityCreatedEvent {

  private UUID eventId;
  private String eventType;
  private LocalDateTime occurredAt;

  private UUID activityId;
  private UUID userId;
  private ActivityType activityType;
  private ActivitySource source;

  private Integer durationMinutes;
  private Double caloriesBurned;
  private Double distanceKm;
  private LocalDateTime startedAt;
}
