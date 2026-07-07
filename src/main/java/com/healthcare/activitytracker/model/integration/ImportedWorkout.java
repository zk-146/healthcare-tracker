package com.healthcare.activitytracker.model.integration;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Provider-neutral representation of a single workout pulled from an external source (currently the
 * Google Health API). {@link com.healthcare.activitytracker.service.GoogleHealthClient} is
 * responsible for turning the provider's wire format into this shape, which keeps all wire-format
 * assumptions in one place and lets the import/mapping logic be tested without any HTTP.
 */
@Getter
@Builder
public class ImportedWorkout {

  /** Stable source record id, used to de-duplicate repeated imports. */
  private final String externalId;

  /** Raw activity-type label from the provider (e.g. "RUN", "WALK"); mapped to our enum. */
  private final String rawType;

  private final LocalDateTime startedAt;
  private final LocalDateTime endedAt;
  private final Integer durationMinutes;
  private final Double distanceKm;
  private final Double caloriesBurned;
  private final Integer heartRateAvg;
  private final Integer steps;
}
