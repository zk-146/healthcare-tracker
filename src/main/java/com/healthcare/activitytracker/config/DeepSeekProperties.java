package com.healthcare.activitytracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the DeepSeek API integration, the {@code app.ai.provider=deepseek} alternative
 * to the local Ollama backend.
 *
 * <p>Bound from {@code app.deepseek.*}. Unlike {@link OllamaProperties}, there is deliberately no
 * {@code apiKey} field here: DeepSeek is a paid cloud API, so each user supplies their own key via
 * Profile > AI settings rather than the deployment sharing one server-wide key. {@code
 * DeepSeekClient} resolves the key per-request from the calling user's (encrypted) profile field.
 */
@Component
@ConfigurationProperties(prefix = "app.deepseek")
public class DeepSeekProperties {

  /** Master switch. When false, {@code DeepSeekClient} short-circuits to empty results. */
  private boolean enabled = true;

  /** DeepSeek's OpenAI-compatible API base URL. */
  private String baseUrl = "https://api.deepseek.com";

  /** Model name, e.g. {@code deepseek-chat}. */
  private String model = "deepseek-chat";

  /** TCP connect timeout. Kept short so an unreachable API fails fast. */
  private int connectTimeoutMs = 5000;

  /** Read timeout for a chat completion. */
  private int readTimeoutMs = 60000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public int getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(int readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }
}
