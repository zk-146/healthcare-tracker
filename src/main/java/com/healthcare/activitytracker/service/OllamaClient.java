package com.healthcare.activitytracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.OllamaProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Thin HTTP wrapper around the Ollama REST API ({@code POST /api/generate}, non-streaming).
 *
 * <p>Never throws: any transport, HTTP, or parse failure is logged at WARN and surfaced as {@link
 * Optional#empty()}, so AI-backed features degrade gracefully when Ollama is down, slow, or
 * misconfigured. Prompts are never logged — they may embed user-entered notes (PII policy).
 */
@Service
public class OllamaClient {

  private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

  private final OllamaProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public OllamaClient(OllamaProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
    requestFactory.setReadTimeout(properties.getReadTimeoutMs());
    this.restClient =
        RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .build();
  }

  /** Generates free-form text for the given prompt. Empty when Ollama is disabled/unavailable. */
  public Optional<String> generate(String prompt) {
    return doGenerate(prompt, false);
  }

  /** Same as {@link #generate}, but instructs the model to emit a single JSON object. */
  public Optional<String> generateJson(String prompt) {
    return doGenerate(prompt, true);
  }

  private Optional<String> doGenerate(String prompt, boolean jsonFormat) {
    if (!properties.isEnabled()) {
      return Optional.empty();
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.getModel());
    body.put("prompt", prompt);
    body.put("stream", false);
    if (jsonFormat) {
      body.put("format", "json");
    }
    try {
      String response =
          restClient
              .post()
              .uri("/api/generate")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(String.class);
      JsonNode text = objectMapper.readTree(response == null ? "{}" : response).path("response");
      if (!text.isTextual() || text.asText().isBlank()) {
        log.warn("Ollama returned no usable 'response' field (model={})", properties.getModel());
        return Optional.empty();
      }
      return Optional.of(text.asText().trim());
    } catch (Exception e) {
      log.warn("Ollama request failed (baseUrl={}): {}", properties.getBaseUrl(), e.getMessage());
      return Optional.empty();
    }
  }
}
