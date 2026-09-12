package com.healthcare.activitytracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.DeepSeekProperties;
import com.healthcare.activitytracker.util.RestClientFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Thin HTTP wrapper around DeepSeek's OpenAI-compatible chat completions API ({@code POST
 * /chat/completions}, non-streaming).
 *
 * <p>Active when {@code app.ai.provider=deepseek} — see {@link AiTextClient}. Unlike Ollama,
 * DeepSeek needs a per-user API key: each call looks up the requesting user's own key via {@link
 * DeepSeekConnectionService} (entered via Profile > AI settings, AES-256-GCM encrypted at rest). A
 * user with no key on file gets an empty result, same as Ollama being unreachable — this never
 * blocks the caller.
 *
 * <p>Never throws: any failure — network, auth, malformed output, missing credentials, or a key
 * that fails to decrypt — is logged at WARN and surfaced as {@link Optional#empty()}. The key
 * lookup happens inside the same try/catch as the network call precisely so a decrypt failure
 * degrades the same way every other failure here does, rather than escaping as an unchecked
 * exception. Prompts are never logged — they may embed user-entered notes (PII policy).
 */
@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "deepseek")
public class DeepSeekClient implements AiTextClient {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

  private final DeepSeekProperties properties;
  private final DeepSeekConnectionService connectionService;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public DeepSeekClient(
      DeepSeekProperties properties,
      DeepSeekConnectionService connectionService,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.connectionService = connectionService;
    this.objectMapper = objectMapper;
    this.restClient =
        RestClientFactory.withTimeouts(
            properties.getBaseUrl(),
            properties.getConnectTimeoutMs(),
            properties.getReadTimeoutMs());
  }

  @Override
  public Optional<String> generate(UUID userId, String prompt) {
    return doGenerate(userId, prompt, false);
  }

  @Override
  public Optional<String> generateJson(UUID userId, String prompt) {
    return doGenerate(userId, prompt, true);
  }

  private Optional<String> doGenerate(UUID userId, String prompt, boolean jsonFormat) {
    if (!properties.isEnabled()) {
      return Optional.empty();
    }
    try {
      Optional<String> apiKey = connectionService.findDecryptedApiKey(userId);
      if (apiKey.isEmpty()) {
        log.warn("No DeepSeek API key on file for user {}", userId);
        return Optional.empty();
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", properties.getModel());
      body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
      body.put("stream", false);
      if (jsonFormat) {
        body.put("response_format", Map.of("type", "json_object"));
      }

      String response =
          restClient
              .post()
              .uri("/chat/completions")
              .header("Authorization", "Bearer " + apiKey.get())
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(String.class);
      JsonNode content =
          objectMapper
              .readTree(response == null ? "{}" : response)
              .path("choices")
              .path(0)
              .path("message")
              .path("content");
      if (!content.isTextual() || content.asText().isBlank()) {
        log.warn("DeepSeek returned no usable content (model={})", properties.getModel());
        return Optional.empty();
      }
      return Optional.of(content.asText().trim());
    } catch (Exception e) {
      // Covers network/HTTP failures from the call above *and* a key that fails to decrypt
      // (e.g. the encryption key was rotated after this one was stored) — both degrade the
      // same way, per the class contract.
      log.warn("DeepSeek request failed (baseUrl={}): {}", properties.getBaseUrl(), e.getMessage());
      return Optional.empty();
    }
  }
}
