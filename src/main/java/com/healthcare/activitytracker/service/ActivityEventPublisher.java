package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around {@link KafkaTemplate} for publishing activity-domain events.
 *
 * <p>Decouples {@link ActivityService} from the Kafka client (easy to mock in tests, easy to swap
 * the transport later without touching business logic).
 *
 * <p>Publishes are send-and-forget from the caller's perspective: the future is observed via {@code
 * whenComplete} for logging, and any failure is swallowed so a Kafka outage cannot fail the HTTP
 * request path. The database is the source of truth; Kafka delivery is best-effort. For stronger
 * guarantees, upgrade to the transactional-outbox pattern.
 */
@Service
public class ActivityEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(ActivityEventPublisher.class);

  private final KafkaTemplate<String, ActivityCreatedEvent> kafkaTemplate;
  private final String topic;

  public ActivityEventPublisher(
      KafkaTemplate<String, ActivityCreatedEvent> kafkaTemplate,
      @Value("${app.kafka.topics.activity-events}") String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  /**
   * Publishes an {@link ActivityCreatedEvent}. Key = userId so all events for the same user land on
   * the same partition (preserving ordering).
   */
  public void publishActivityCreated(ActivityCreatedEvent event) {
    try {
      kafkaTemplate
          .send(topic, event.getUserId().toString(), event)
          .whenComplete(
              (result, ex) -> {
                if (ex != null) {
                  log.error(
                      "Failed to publish ActivityCreatedEvent eventId={} activityId={}",
                      event.getEventId(),
                      event.getActivityId(),
                      ex);
                } else {
                  log.info(
                      "Published ActivityCreatedEvent eventId={} activityId={} partition={} offset={}",
                      event.getEventId(),
                      event.getActivityId(),
                      result.getRecordMetadata().partition(),
                      result.getRecordMetadata().offset());
                }
              });
    } catch (Exception e) {
      log.error(
          "Kafka publish threw synchronously for eventId={} — swallowing to protect request path",
          event.getEventId(),
          e);
    }
  }
}
