package com.healthcare.activitytracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.OutboxProperties;
import com.healthcare.activitytracker.model.entity.OutboxEvent;
import com.healthcare.activitytracker.model.enums.OutboxStatus;
import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import com.healthcare.activitytracker.repository.OutboxEventRepository;
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

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
  @Transactional
  public void drain() {
    List<OutboxEvent> rows = outboxRepository.claimPending(properties.getBatchSize());
    if (rows.isEmpty()) {
      return;
    }

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
          entry.getValue().get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
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

  private void recordFailure(OutboxEvent row, Exception e) {
    row.setAttempts(row.getAttempts() + 1);
    row.setLastError(truncate(e.toString()));

    if (row.getAttempts() >= properties.getMaxAttempts()) {
      row.setStatus(OutboxStatus.FAILED);
      log.error(
          "Outbox row {} parked as FAILED after {} attempts eventId={} lastError={}",
          row.getId(),
          row.getAttempts(),
          row.getEventId(),
          row.getLastError());
    } else {
      log.warn(
          "Outbox publish failed for row {} on attempt {} eventId={}",
          row.getId(),
          row.getAttempts(),
          row.getEventId(),
          e);
    }
  }

  private static String truncate(String value) {
    return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
  }
}
