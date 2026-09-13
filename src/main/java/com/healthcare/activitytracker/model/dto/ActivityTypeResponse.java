package com.healthcare.activitytracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One selectable activity type, exposed so clients need not hardcode the enum. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityTypeResponse {

  /** The enum constant name, e.g. {@code STRENGTH_TRAINING}. This is what write requests send. */
  private String name;

  /** Human-readable label derived from the name, e.g. {@code Strength Training}. */
  private String label;

  /**
   * The MET constant used for calorie estimation. Exposed so a client can show a live estimate
   * before submitting; see the spec for the tradeoff this accepts.
   */
  private Double metValue;
}
