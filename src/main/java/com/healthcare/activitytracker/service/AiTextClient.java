package com.healthcare.activitytracker.service;

import java.util.Optional;
import java.util.UUID;

/**
 * A pluggable text-generation backend for the app's AI-assisted features (activity digests,
 * milestone copy, notes analysis). Exactly one implementation is active at a time, selected by
 * {@code app.ai.provider} ({@link OllamaClient} for {@code ollama}, the default; {@link
 * DeepSeekClient} for {@code deepseek}).
 *
 * <p>{@code userId} is passed on every call because a provider's credentials may be per-user (a
 * user's own DeepSeek key from their Profile) rather than a single server-wide config (Ollama has
 * no such concept and ignores it). It may be {@code null} for call sites with no user context
 * (there are none today, but the contract allows it); a provider that requires a user must then
 * return {@link Optional#empty()} rather than throw, matching the "never blocks the caller"
 * contract every implementation follows.
 *
 * <p>Never throws: any failure — network, auth, malformed output, missing credentials — is an
 * empty {@link Optional}, so every caller already has graceful-degradation logic built in.
 */
public interface AiTextClient {

  /** Generates free-form text for the given prompt. Empty when the provider is unavailable. */
  Optional<String> generate(UUID userId, String prompt);

  /** Same as {@link #generate}, but instructs the model to emit a single JSON object. */
  Optional<String> generateJson(UUID userId, String prompt);
}
