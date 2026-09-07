package com.healthcare.activitytracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the transactional outbox relay.
 *
 * <p>Bound from {@code app.outbox.*}. Note that {@code poll-interval-ms} and {@code
 * purge-interval-ms} are not read from this object: {@code @Scheduled(fixedDelayString = ...)} is
 * resolved at bean-registration time and cannot read a bound properties bean, so those two are
 * referenced as {@code ${...}} placeholders directly on the annotations.
 */
@Component
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

  /** Master switch for the relay. When false the relay bean is not registered at all. */
  private boolean enabled = true;

  /** Maximum rows claimed per poll. Bounds the lock window and the in-flight send batch. */
  private int batchSize = 100;

  /** Failed publishes after which a row is parked as FAILED and stops blocking the queue. */
  private int maxAttempts = 10;

  /** Time budget for the whole in-flight batch to be acknowledged, not per row. */
  private int sendTimeoutMs = 10000;

  /** How long SENT rows are retained before the purge job deletes them. */
  private int retentionDays = 7;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public int getSendTimeoutMs() {
    return sendTimeoutMs;
  }

  public void setSendTimeoutMs(int sendTimeoutMs) {
    this.sendTimeoutMs = sendTimeoutMs;
  }

  public int getRetentionDays() {
    return retentionDays;
  }

  public void setRetentionDays(int retentionDays) {
    this.retentionDays = retentionDays;
  }
}
