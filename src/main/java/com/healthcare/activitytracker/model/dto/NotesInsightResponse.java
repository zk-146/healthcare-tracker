package com.healthcare.activitytracker.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotesInsightResponse {

  private UUID activityId;

  /** False when there are no notes or the analysis could not run. */
  private boolean available;

  /** One of: positive, neutral, negative, unknown. Null when unavailable. */
  private String mood;

  private Boolean painMentioned;

  /** Short description of the pain mention, if any. */
  private String painDescription;

  /** Human-readable status when {@code available} is false. */
  private String message;
}
