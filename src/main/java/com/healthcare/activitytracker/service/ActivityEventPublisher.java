package com.healthcare.activitytracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.model.entity.OutboxEvent;
import com.healthcare.activitytracker.model.enums.OutboxStatus;
import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import com.healthcare.activitytracker.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enqueues activity-domain events into the transactional outbox.
 *
 * <p>This used to call {@link org.springframework.kafka.core.KafkaTemplate} directly, which
 * published from inside the caller's transaction — a consumer could read the event and evaluate a
 * streak before the activity row committed. The event is now inserted in the same transaction as
 * the activity and published afterwards by {@code OutboxRelay}, so it cannot become visible early.
 *
 * <p>Unlike the old implementation, failures here are <strong>not</strong> swallowed. A Kafka
 * outage can no longer fail a request because Kafka is no longer on the request path at all; but a
 * failed outbox insert must roll the transaction back, since committing an activity without its
 * event reintroduces exactly the silent loss this design removes.
 */
@Service
public class ActivityEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(ActivityEventPublisher.class);

  private final OutboxEventRepository outboxRepository;
  private final ObjectMapper objectMapper;
  private final String topic;

  public ActivityEventPublisher(
      OutboxEventRepository outboxRepository,
      ObjectMapper objectMapper,
      @Value("${app.kafka.topics.activity-events}") String topic) {
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
    this.topic = topic;
  }

  /**
   * Writes an {@link ActivityCreatedEvent} to the outbox. Key = userId so all events for the same
   * user land on the same partition when the relay publishes them, preserving ordering.
   *
   * <p>{@link Propagation#MANDATORY} is the point of the whole design: this must run inside the
   * caller's transaction. Calling it without one is a programming error and fails loudly rather
   * than silently reopening the race.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void publishActivityCreated(ActivityCreatedEvent event) {
    String payload;
    try {
      payload = objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Failed to serialize ActivityCreatedEvent eventId=" + event.getEventId(), e);
    }

    outboxRepository.save(
        OutboxEvent.builder()
            .eventId(event.getEventId())
            .aggregateId(event.getActivityId())
            .partitionKey(event.getUserId().toString())
            .topic(topic)
            .eventType(event.getEventType())
            .payload(payload)
            .status(OutboxStatus.PENDING)
            .attempts(0)
            .build());

    log.debug(
        "Enqueued ActivityCreatedEvent eventId={} activityId={} to outbox",
        event.getEventId(),
        event.getActivityId());
  }
}
