package com.healthcare.activitytracker.service;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Produces the celebratory copy for streak-milestone notifications. Uses the configured AI provider
 * ({@link OllamaClient} or {@link DeepSeekClient}, per {@code app.ai.provider}) when available and
 * falls back to a static template otherwise — a milestone notification is never blocked by the AI
 * layer.
 *
 * <p>The prompt deliberately contains no user PII (no name/email), so nothing personal is sent to
 * the model and the resulting copy is safe to log under the identifiers-only policy. {@code userId}
 * is only needed to resolve a per-user provider credential (DeepSeek); it never appears in the
 * prompt itself.
 */
@Service
public class MilestoneMessageService {

  private final AiTextClient aiTextClient;

  public MilestoneMessageService(AiTextClient aiTextClient) {
    this.aiTextClient = aiTextClient;
  }

  /** Returns celebratory copy for the given streak length. Never null. */
  public String milestoneMessage(UUID userId, int streakDays) {
    String prompt =
        "Write a short celebratory message (1-2 sentences, max 40 words) for a fitness app user "
            + "who just reached a "
            + streakDays
            + "-day activity streak. Be warm and motivating. Do not use a personal name. "
            + "Reply with the message only.";
    return aiTextClient.generate(userId, prompt).orElse(fallbackMessage(streakDays));
  }

  static String fallbackMessage(int streakDays) {
    return "Congratulations! You've logged activity "
        + streakDays
        + " days in a row. Keep the streak alive!";
  }
}
