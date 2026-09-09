package com.healthcare.activitytracker.model.dto;

import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.validation.ValidDateRange;
import com.healthcare.activitytracker.validation.ValidDeviceId;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@ValidDateRange
@ValidDeviceId
public class ActivityRequest {

  @NotNull(message = "Activity type is required")
  private ActivityType activityType;

  @NotNull(message = "Source is required")
  private ActivitySource source;

  private String deviceId;

  /**
   * Zoneless wall-clock time in the caller's own timezone (the {@code X-User-Timezone} header) —
   * not the server's. Bean Validation has no access to that header, so "not in the future" is
   * checked in {@link com.healthcare.activitytracker.service.ActivityService} against the caller's
   * resolved zone, not with {@code @PastOrPresent} here (which would compare against the server's
   * own clock and wrongly reject a valid same-day time for any user ahead of it).
   */
  @NotNull(message = "Start time is required")
  private LocalDateTime startedAt;

  /**
   * Same zoneless, caller-timezone convention as {@link #startedAt}, and the same reason its "not
   * in the future" check lives in the service layer rather than as {@code @PastOrPresent} here.
   * Must not precede startedAt (enforced by @ValidDateRange).
   */
  private LocalDateTime endedAt;

  /** Max 1440 min (24 h). If endedAt is also provided, must be consistent with elapsed time. */
  @Min(value = 1, message = "Duration must be at least 1 minute")
  @Max(value = 1440, message = "Duration cannot exceed 1440 minutes (24 hours)")
  private Integer durationMinutes;

  @DecimalMin(value = "0.0", inclusive = false, message = "Distance must be greater than 0")
  @DecimalMax(value = "1000.0", message = "Distance cannot exceed 1000 km")
  private Double distanceKm;

  @DecimalMin(value = "0.0", inclusive = false, message = "Calories must be greater than 0")
  @DecimalMax(value = "10000.0", message = "Calories cannot exceed 10000")
  private Double caloriesBurned;

  /** Realistic human range: 1–300 bpm. */
  @Min(value = 1, message = "Heart rate must be at least 1 bpm")
  @Max(value = 300, message = "Heart rate cannot exceed 300 bpm")
  private Integer heartRateAvg;

  @Min(value = 1, message = "Steps must be at least 1")
  @Max(value = 100_000, message = "Steps cannot exceed 100,000")
  private Integer steps;

  @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
  private String notes;
}
