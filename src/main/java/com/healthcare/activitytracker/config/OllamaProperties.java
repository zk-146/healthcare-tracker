package com.healthcare.activitytracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the local Ollama LLM integration (AI digests, milestone copy, notes analysis).
 *
 * <p>Bound from {@code app.ollama.*}. All AI features degrade gracefully when Ollama is unreachable
 * or {@code enabled=false}: callers receive an empty result and fall back to static copy or an
 * "insights unavailable" response.
 */
@Component
@ConfigurationProperties(prefix = "app.ollama")
public class OllamaProperties {

  /** Master switch. When false, {@code OllamaClient} short-circuits to empty results. */
  private boolean enabled = true;

  /** Base URL of the Ollama server. In docker-compose this is {@code http://ollama:11434}. */
  private String baseUrl = "http://localhost:11434";

  /** Model tag used for all generations. Must already be pulled on the server. */
  private String model = "qwen2.5:14b";

  /** TCP connect timeout. Kept short so a down Ollama fails fast. */
  private int connectTimeoutMs = 3000;

  /** Read timeout. Generation on modest hardware can take a while. */
  private int readTimeoutMs = 120000;

  /**
   * Sampling temperature for free-form prose (activity digests, milestone copy). Higher values give
   * more variety between users. JSON generations ignore this and always run at 0.
   */
  private double temperature = 0.7;

  /**
   * Seed for JSON generations. Structured extraction runs deterministically so the same note always
   * yields the same verdict, which is what makes it regression-testable and auditable.
   */
  private int seed = 42;

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

  public double getTemperature() {
    return temperature;
  }

  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  public int getSeed() {
    return seed;
  }

  public void setSeed(int seed) {
    this.seed = seed;
  }
}
