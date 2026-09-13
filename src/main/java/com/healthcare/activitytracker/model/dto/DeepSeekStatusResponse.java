package com.healthcare.activitytracker.model.dto;

import lombok.Builder;
import lombok.Getter;

/** Reports whether the owner has configured their own DeepSeek API key. */
@Getter
@Builder
public class DeepSeekStatusResponse {

  /** True if a key is on file. The key itself is never returned. */
  private final boolean connected;
}
