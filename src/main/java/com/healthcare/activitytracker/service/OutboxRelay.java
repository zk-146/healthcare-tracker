package com.healthcare.activitytracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.OutboxProperties;
import com.healthcare.activitytracker.model.entity.OutboxEvent;
import com.healthcare.activitytracker.model.enums.OutboxStatus;
import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import com.healthcare.activitytracker.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the transactional outbox to Kafka.
 *
 * <p>Claims a batch of {@code PENDING} rows with {@code FOR UPDATE SKIP LOCKED}, fires every send
 * before awaiting any future, then records the outcome per row. Firing first matters: awaiting each
 * send in turn would make the worst case one timeout <em>per row</em> while holding row locks,
 * whereas this way the whole batch shares a single timeout window and the lock window stays in the
 * milliseconds under normal conditions.
 *
 * <p>Nothing here does crash recovery, because none is needed. A crash mid-drain drops the
 * transaction and its locks, leaving the rows {@code PENDING} and indistinguishable from fresh ones
 * — the next poll simply picks them up.
 */
@Service
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  /**
   * {@code last_error} is TEXT and has no storage limit, but a full Kafka stack trace repeated
   * across a 100-row batch is noise, not diagnostic value.
   */
  private static final int MAX_ERROR_LENGTH = 2000;

  /** Ceiling on the exponential retry backoff between attempts. */
  private static final long MAX_BACKOFF_SECONDS = 300;

  private final OutboxEventRepository outboxRepository;
  private final KafkaTemplate<String, ActivityCreatedEvent> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final OutboxProperties properties;

  public OutboxRelay(
      OutboxEventRepository outboxRepository,
      KafkaTemplate<String, ActivityCreatedEvent> kafkaTemplate,
      ObjectMapper objectMapper,
      OutboxProperties properties) {
    this.outboxRepository = outboxRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  /**
   * Claims a batch, publishes it, and records the outcome — all inside one transaction holding
   * {@code FOR UPDATE} locks on the claimed rows. Every blocking call in here is therefore bounded
   * deliberately.
   *
   * <p><strong>The await phase.</strong> {@code sendTimeoutMs} bounds the batch <em>as a
   * whole</em>, not each {@code Future.get} in isolation. One deadline is established before the
   * first send is fired, and every await computes what remains of it, so N rows share a single
   * timeout window instead of getting N independent ones. Passing the full {@code sendTimeoutMs} to
   * each {@code get} would let a stalled batch of 100 rows hold their locks for 100 times the
   * configured budget.
   *
   * <p><strong>The fire phase.</strong> That deadline cannot bound the fire loop, because {@code
   * KafkaProducer.send} blocks in {@code waitOnMetadata} rather than handing back a future to
   * await. It is bounded instead by the producer settings in {@code application.yml}: {@code
   * max.block.ms=5000} caps how long a single {@code send} waits for metadata, and {@code
   * delivery.timeout.ms=15000} guarantees a send that did get metadata eventually completes its
   * future rather than hanging. Both are set explicitly because the Kafka defaults — 60s and 120s —
   * are far too long to sit on inside an open transaction.
   *
   * <p>Note that {@code kafkaTemplate.flush()} already blocks until every in-flight future
   * completes, so in the normal case the await loop finds the budget untouched. It exists to bound
   * the pathological case, not the common one.
   */
  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
  @Transactional
  public void drain() {
    List<OutboxEvent> rows = outboxRepository.claimPending(properties.getBatchSize());
    if (rows.isEmpty()) {
      return;
    }

    long deadlineNanos =
        System.nanoTime() + Duration.ofMillis(properties.getSendTimeoutMs()).toNanos();

    Map<OutboxEvent, CompletableFuture<SendResult<String, ActivityCreatedEvent>>> inFlight =
        new LinkedHashMap<>();
    for (OutboxEvent row : rows) {
      try {
        ActivityCreatedEvent event =
            objectMapper.readValue(row.getPayload(), ActivityCreatedEvent.class);
        inFlight.put(row, kafkaTemplate.send(row.getTopic(), row.getPartitionKey(), event));
      } catch (Exception e) {
        // A payload that will not deserialize can never succeed, but it must not escape this
        // method — one poison row would stop the poller for every other user.
        recordFailure(row, e);
      }
    }

    if (!inFlight.isEmpty()) {
      kafkaTemplate.flush();
      for (Map.Entry<OutboxEvent, CompletableFuture<SendResult<String, ActivityCreatedEvent>>>
          entry : inFlight.entrySet()) {
        OutboxEvent row = entry.getKey();
        try {
          entry
              .getValue()
              .get(remainingBudgetMillis(deadlineNanos, System.nanoTime()), TimeUnit.MILLISECONDS);
          row.setStatus(OutboxStatus.SENT);
          row.setSentAt(LocalDateTime.now(ZoneOffset.UTC));
          log.debug("Published outbox row {} eventId={}", row.getId(), row.getEventId());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          recordFailure(row, e);
        } catch (Exception e) {
          recordFailure(row, e);
        }
      }
    }

    outboxRepository.saveAll(rows);
  }

  /**
   * Milliseconds left of the shared send deadline for a batch, floored at zero.
   *
   * <p>Package-private so the arithmetic is unit-testable with fixed values instead of by racing a
   * wall clock. Zero means poll the future and give up immediately: the batch budget is spent, and
   * any row still unacknowledged is recorded as a failure and retried on a later poll.
   */
  static long remainingBudgetMillis(long deadlineNanos, long nowNanos) {
    long remainingNanos = deadlineNanos - nowNanos;
    return remainingNanos <= 0 ? 0L : TimeUnit.NANOSECONDS.toMillis(remainingNanos);
  }

  /**
   * Deletes published rows past the retention window, so the outbox does not grow without bound.
   *
   * <p>Only {@code SENT} rows are eligible. {@code FAILED} rows are retained indefinitely and on
   * purpose: they are the record of events that never reached Kafka, and deleting them would
   * destroy the only evidence that something was lost.
   */
  @Scheduled(fixedDelayString = "${app.outbox.purge-interval-ms:86400000}")
  @Transactional
  public void purgeSent() {
    LocalDateTime cutoff =
        LocalDateTime.now(ZoneOffset.UTC).minusDays(properties.getRetentionDays());
    int deleted = outboxRepository.purgeSentBefore(OutboxStatus.SENT, cutoff);
    if (deleted > 0) {
      log.info("Purged {} sent outbox rows older than {}", deleted, cutoff);
    }
  }

  private void recordFailure(OutboxEvent row, Exception e) {
    row.setAttempts(row.getAttempts() + 1);
    row.setLastError(truncate(e.toString()));

    if (row.getAttempts() >= properties.getMaxAttempts()) {
      row.setStatus(OutboxStatus.FAILED);
      // No nextAttemptAt here: claimPending filters on status = PENDING, so a parked row is never
      // reconsidered and a backoff on it would mean nothing.
      log.error(
          "Outbox row {} parked as FAILED after {} attempts eventId={} lastError={}",
          row.getId(),
          row.getAttempts(),
          row.getEventId(),
          row.getLastError());
    } else {
      long backoff = backoffSeconds(row.getAttempts());
      row.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(backoff));
      log.warn(
          "Outbox publish failed for row {} on attempt {} eventId={}, retrying in {}s",
          row.getId(),
          row.getAttempts(),
          row.getEventId(),
          backoff,
          e);
    }
  }

  /**
   * Exponential backoff between retry attempts, capped at {@link #MAX_BACKOFF_SECONDS}.
   *
   * <p>Without a backoff a failing row is retried every poll interval and parks as {@code FAILED}
   * after only {@code maxAttempts} times {@code poll-interval-ms} — about ten seconds at the
   * shipped defaults, far shorter than any real Kafka outage. Doubling stretches the same ten
   * attempts across roughly half an hour.
   */
  static long backoffSeconds(int attempts) {
    double doubled = Math.pow(2, attempts);
    return doubled >= MAX_BACKOFF_SECONDS ? MAX_BACKOFF_SECONDS : (long) doubled;
  }

  private static String truncate(String value) {
    return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
  }
}
