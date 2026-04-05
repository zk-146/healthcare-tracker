package com.healthcare.activitytracker.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka configuration.
 *
 * <p>Producer, consumer, and listener container factories are auto-configured by Spring Boot from
 * {@code application.yml} (cleaner than hand-rolling beans and matches Spring Boot idioms). This
 * class only declares topics so they are auto-created on application startup — no manual {@code
 * kafka-topics.sh} step.
 *
 * <p>{@code activity-events} uses 3 partitions (keyed by {@code userId} to preserve per-user
 * ordering) and replication factor 1 (demo-appropriate; production would use at least 3).
 */
@Configuration
public class KafkaConfig {

  private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

  private final String activityEventsTopic;

  public KafkaConfig(@Value("${app.kafka.topics.activity-events}") String activityEventsTopic) {
    this.activityEventsTopic = activityEventsTopic;
  }

  @Bean
  public NewTopic activityEventsTopic() {
    return TopicBuilder.name(activityEventsTopic).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic activityEventsDlqTopic() {
    return TopicBuilder.name(activityEventsTopic + ".DLQ").partitions(3).replicas(1).build();
  }

  @Bean
  public CommonErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
    // Retry up to 3 times with 1-second intervals before sending to DLQ
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    errorHandler.setRetryListeners(
        (record, ex, deliveryAttempt) ->
            log.warn(
                "Kafka retry attempt {} for topic={} partition={} offset={}",
                deliveryAttempt,
                record.topic(),
                record.partition(),
                record.offset(),
                ex));
    return errorHandler;
  }
}
