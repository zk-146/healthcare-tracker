package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.enums.ActivityType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps a provider's raw activity-type label (from the Google Health API / Fitbit) onto our {@link
 * ActivityType} enum. Anything unrecognised falls back to {@link ActivityType#OTHER}, so a new or
 * unexpected exercise type can never break an import.
 */
@Component
public class ActivityTypeMapper {

  private static final Map<String, ActivityType> LOOKUP =
      Map.ofEntries(
          Map.entry("WALK", ActivityType.WALKING),
          Map.entry("WALKING", ActivityType.WALKING),
          Map.entry("HIKE", ActivityType.WALKING),
          Map.entry("RUN", ActivityType.RUNNING),
          Map.entry("RUNNING", ActivityType.RUNNING),
          Map.entry("TREADMILL", ActivityType.RUNNING),
          Map.entry("YOGA", ActivityType.YOGA),
          Map.entry("BIKE", ActivityType.CYCLING),
          Map.entry("BIKING", ActivityType.CYCLING),
          Map.entry("CYCLING", ActivityType.CYCLING),
          Map.entry("SPINNING", ActivityType.CYCLING),
          Map.entry("SWIM", ActivityType.SWIMMING),
          Map.entry("SWIMMING", ActivityType.SWIMMING),
          Map.entry("WEIGHTS", ActivityType.STRENGTH_TRAINING),
          Map.entry("STRENGTH_TRAINING", ActivityType.STRENGTH_TRAINING),
          Map.entry("WEIGHTLIFTING", ActivityType.STRENGTH_TRAINING),
          Map.entry("STRETCH", ActivityType.STRETCHING),
          Map.entry("STRETCHING", ActivityType.STRETCHING));

  /** Resolves a raw label to an {@link ActivityType}; null/blank/unknown become {@code OTHER}. */
  public ActivityType map(String rawType) {
    if (rawType == null || rawType.isBlank()) {
      return ActivityType.OTHER;
    }
    return LOOKUP.getOrDefault(rawType.trim().toUpperCase(), ActivityType.OTHER);
  }
}
