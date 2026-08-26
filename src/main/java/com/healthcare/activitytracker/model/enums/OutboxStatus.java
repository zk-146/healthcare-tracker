package com.healthcare.activitytracker.model.enums;

/** Lifecycle of a row in {@code activity_outbox}. */
public enum OutboxStatus {
  /** Written by the producing transaction, not yet published. */
  PENDING,
  /** Successfully published to Kafka. */
  SENT,
  /** Exceeded the attempt cap. Skipped by the relay; retained for inspection. */
  FAILED
}
