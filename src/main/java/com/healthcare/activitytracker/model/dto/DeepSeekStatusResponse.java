package com.healthcare.activitytracker.model.dto;

import lombok.Builder;
import lombok.Getter;

/** Reports whether the owner has configured their own DeepSeek API key, and whether it is used. */
@Getter
@Builder
public class DeepSeekStatusResponse {

  /** True if a key is on file. The key itself is never returned. */
  private final boolean connected;

  /**
   * True only when the server's AI provider is DeepSeek ({@code app.ai.provider=deepseek}). The
   * provider is chosen server-wide at startup, so under any other value a saved key is stored but
   * never used — the UI needs this to say so instead of silently accepting the key.
   */
  private final boolean active;
}
