package com.healthcare.activitytracker.model.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DigestResponse {

  /** One of: daily, weekly, monthly. */
  private String period;

  private LocalDate from;
  private LocalDate to;

  /** False when the digest could not be generated (Ollama down/disabled). */
  private boolean available;

  /** The generated digest, or a human-readable fallback message when unavailable. */
  private String digest;
}
