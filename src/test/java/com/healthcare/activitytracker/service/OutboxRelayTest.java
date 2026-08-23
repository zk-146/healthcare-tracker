package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.activitytracker.config.OutboxProperties;
import com.healthcare.activitytracker.model.entity.OutboxEvent;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.model.enums.OutboxStatus;
import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import com.healthcare.activitytracker.repository.OutboxEventRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  private static final String TOPIC = "activity-events";

  @Mock private OutboxEventRepository outboxRepository;
  @Mock private KafkaTemplate<String, ActivityCreatedEvent> kafkaTemplate;

  private ObjectMapper objectMapper;
  private OutboxProperties properties;
  private OutboxRelay relay;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    properties = new OutboxProperties();
    properties.setBatchSize(100);
    properties.setMaxAttempts(3);
    properties.setSendTimeoutMs(1000);

    relay = new OutboxRelay(outboxRepository, kafkaTemplate, objectMapper, properties);
  }

  private String payloadJson() {
    try {
      return objectMapper.writeValueAsString(
          ActivityCreatedEvent.builder()
              .eventId(UUID.randomUUID())
              .eventType("ACTIVITY_CREATED")
              .occurredAt(LocalDateTime.now())
              .activityId(UUID.randomUUID())
              .userId(UUID.randomUUID())
              .activityType(ActivityType.RUNNING)
              .source(ActivitySource.MANUAL)
              .durationMinutes(30)
              .startedAt(LocalDateTime.now())
              .build());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private OutboxEvent pendingRow(String payload, int attempts) {
    return OutboxEvent.builder()
        .id(1L)
        .eventId(UUID.randomUUID())
        .aggregateId(UUID.randomUUID())
        .partitionKey(UUID.randomUUID().toString())
        .topic(TOPIC)
        .eventType("ACTIVITY_CREATED")
        .payload(payload)
        .status(OutboxStatus.PENDING)
        .attempts(attempts)
        .build();
  }

  @SuppressWarnings("unchecked")
  private void stubSendSucceeding() {
    when(kafkaTemplate.send(anyString(), anyString(), any(ActivityCreatedEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
  }

  private void stubSendFailing() {
    when(kafkaTemplate.send(anyString(), anyString(), any(ActivityCreatedEvent.class)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
  }

  @Test
  void marksRowSentOnSuccessfulPublish() {
    OutboxEvent row = pendingRow(payloadJson(), 0);
    when(outboxRepository.claimPending(anyInt())).thenReturn(List.of(row));
    stubSendSucceeding();

    relay.drain();

    assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(row.getSentAt()).isNotNull();
    assertThat(row.getAttempts()).isZero();
    verify(outboxRepository).saveAll(List.of(row));
  }

  @Test
  void publishesWithTheStoredTopicAndPartitionKey() {
    OutboxEvent row = pendingRow(payloadJson(), 0);
    when(outboxRepository.claimPending(anyInt())).thenReturn(List.of(row));
    stubSendSucceeding();

    relay.drain();

    verify(kafkaTemplate)
        .send(eq(TOPIC), eq(row.getPartitionKey()), any(ActivityCreatedEvent.class));
  }

  @Test
  void incrementsAttemptsAndKeepsRowPendingWhenSendFails() {
    OutboxEvent row = pendingRow(payloadJson(), 0);
    when(outboxRepository.claimPending(anyInt())).thenReturn(List.of(row));
    stubSendFailing();

    relay.drain();

    assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(row.getAttempts()).isEqualTo(1);
    assertThat(row.getLastError()).contains("broker down");
    assertThat(row.getSentAt()).isNull();
    verify(outboxRepository).saveAll(List.of(row));
  }

  @Test
  void parksRowAsFailedOnceAttemptsReachTheCap() {
    // maxAttempts is 3 and this row has already failed twice.
    OutboxEvent row = pendingRow(payloadJson(), 2);
    when(outboxRepository.claimPending(anyInt())).thenReturn(List.of(row));
    stubSendFailing();

    relay.drain();

    assertThat(row.getAttempts()).isEqualTo(3);
    assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
  }

  @Test
  void treatsAnUndeserializablePayloadAsAFailureInsteadOfThrowing() {
    // One poison row must not kill the poller for every other user.
    OutboxEvent poison = pendingRow("this is not json", 0);
    when(outboxRepository.claimPending(anyInt())).thenReturn(List.of(poison));

    relay.drain();

    assertThat(poison.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(poison.getAttempts()).isEqualTo(1);
    assertThat(poison.getLastError()).isNotBlank();
    verify(kafkaTemplate, never()).send(anyString(), anyString(), any(ActivityCreatedEvent.class));
    verify(outboxRepository).saveAll(List.of(poison));
  }

  @Test
  void doesNothingWhenNoRowsArePending() {
    when(outboxRepository.claimPending(anyInt())).thenReturn(List.of());

    relay.drain();

    verifyNoInteractions(kafkaTemplate);
    verify(outboxRepository, never()).saveAll(any());
  }

  @Test
  void claimsUsingTheConfiguredBatchSize() {
    properties.setBatchSize(25);
    when(outboxRepository.claimPending(25)).thenReturn(List.of());

    relay.drain();

    verify(outboxRepository).claimPending(25);
  }

  @Test
  void purgeDeletesSentRowsOlderThanTheRetentionWindow() {
    properties.setRetentionDays(7);
    when(outboxRepository.purgeSentBefore(eq(OutboxStatus.SENT), any(LocalDateTime.class)))
        .thenReturn(4);

    relay.purgeSent();

    ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(outboxRepository).purgeSentBefore(eq(OutboxStatus.SENT), cutoff.capture());
    // The cutoff is 7 days back; allow a minute of slack for clock movement during the test.
    assertThat(cutoff.getValue())
        .isCloseTo(
            LocalDateTime.now(ZoneOffset.UTC).minusDays(7),
            within(1, java.time.temporal.ChronoUnit.MINUTES));
  }

  @Test
  void purgeIsHarmlessWhenThereIsNothingToDelete() {
    when(outboxRepository.purgeSentBefore(eq(OutboxStatus.SENT), any(LocalDateTime.class)))
        .thenReturn(0);

    relay.purgeSent();

    verify(outboxRepository).purgeSentBefore(eq(OutboxStatus.SENT), any(LocalDateTime.class));
    verifyNoInteractions(kafkaTemplate);
  }
}
