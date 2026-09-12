package com.healthcare.activitytracker.service;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Fallback {@link AiTextClient} that always returns {@link Optional#empty()}.
 *
 * <p>{@link OllamaClient} and {@link DeepSeekClient} are each gated by
 * {@code @ConditionalOnProperty} on {@code app.ai.provider}, matching exactly {@code "ollama"} or
 * {@code "deepseek"}. An operator typo, unexpected casing, or an unsupported value in between
 * satisfies neither condition — without this bean, that leaves zero {@code AiTextClient}
 * implementations registered, and every consumer ({@link ActivityDigestService}, {@link
 * MilestoneMessageService}, {@link NotesAnalysisService}) takes one as a required constructor
 * argument, so the whole application would fail to start over what should only ever disable AI
 * features. This bean only activates when neither of the other two did
 * ({@code @ConditionalOnMissingBean}), so a valid configuration never sees it; an invalid one gets
 * a running app with AI features off (the same degraded state {@code OLLAMA_ENABLED=false} already
 * produces) instead of an outage.
 */
@Service
@ConditionalOnMissingBean(AiTextClient.class)
public class NoopAiTextClient implements AiTextClient {

  private static final Logger log = LoggerFactory.getLogger(NoopAiTextClient.class);

  public NoopAiTextClient() {
    log.error(
        "app.ai.provider is not set to a recognized value (\"ollama\" or \"deepseek\") -- AI "
            + "features (digests, milestone copy, notes analysis) are disabled until it is fixed.");
  }

  @Override
  public Optional<String> generate(UUID userId, String prompt) {
    return Optional.empty();
  }

  @Override
  public Optional<String> generateJson(UUID userId, String prompt) {
    return Optional.empty();
  }
}
