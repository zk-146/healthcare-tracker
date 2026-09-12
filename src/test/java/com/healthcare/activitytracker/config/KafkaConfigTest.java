package com.healthcare.activitytracker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaConfigTest {

  private KafkaConfig kafkaConfig;

  @BeforeEach
  void setUp() {
    kafkaConfig = new KafkaConfig("activity-events");
  }

  @Test
  void activityEventsTopic_hasThreePartitionsAndReplicationFactorOne() {
    NewTopic topic = kafkaConfig.activityEventsTopic();

    assertThat(topic.name()).isEqualTo("activity-events");
    assertThat(topic.numPartitions()).isEqualTo(3);
    assertThat(topic.replicationFactor()).isEqualTo((short) 1);
  }

  @Test
  void activityEventsDltTopic_isNamedFromTheSourceTopicAndMatchesItsPartitionCount() {
    NewTopic dlt = kafkaConfig.activityEventsDltTopic();

    assertThat(dlt.name()).isEqualTo("activity-events.DLT");
    assertThat(dlt.numPartitions()).isEqualTo(3);
    assertThat(dlt.replicationFactor()).isEqualTo((short) 1);
  }

  @Test
  void kafkaErrorHandler_buildsADeadLetterBackedDefaultErrorHandler() {
    @SuppressWarnings("unchecked")
    KafkaOperations<Object, Object> kafkaTemplate = mock(KafkaOperations.class);

    CommonErrorHandler errorHandler = kafkaConfig.kafkaErrorHandler(kafkaTemplate);

    assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);
  }
}
