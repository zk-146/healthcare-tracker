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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
    return pendingRow(1L, payload, attempts);
  }

  private OutboxEvent pendingRow(long id, String payload, int attempts) {
    return OutboxEvent.builder()
        .id(id)
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
    // A retry must wait out a backoff rather than being re-claimed on the very next poll: after
    // one failed attempt that is 2^1 seconds.
    assertThat(row.getNextAttemptAt())
        .isCloseTo(
            LocalDateTime.now(ZoneOffset.UTC).plusSeconds(OutboxRelay.backoffSeconds(1)),
            within(5, ChronoUnit.SECONDS));
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
    // No backoff is scheduled for a parked row — claimPending filters on PENDING, so the column
    // would never be read again.
    assertThat(row.getNextAttemptAt()).isNull();
  }

  @Test
  void backoffDoublesPerAttemptAndIsCapped() {
    assertThat(OutboxRelay.backoffSeconds(1)).isEqualTo(2);
    assertThat(OutboxRelay.backoffSeconds(2)).isEqualTo(4);
    assertThat(OutboxRelay.backoffSeconds(8)).isEqualTo(256);
    // Capped at five minutes, so a long outage does not push the last attempts hours apart.
    assertThat(OutboxRelay.backoffSeconds(9)).isEqualTo(300);
    assertThat(OutboxRelay.backoffSeconds(40)).isEqualTo(300);
  }

  @Test
  void awaitBudgetIsSharedAcrossTheBatchInsteadOfRestartingPerRow() {
    // The regression: passing the full sendTimeoutMs to every Future.get would let a stalled
    // batch of N rows hold its row locks for N * sendTimeoutMs. One deadline is established
    // before the first send, and each await gets only what is left of it.
    long start = 1_000_000_000L;
    long deadline = start + Duration.ofMillis(1000).toNanos();

    assertThat(OutboxRelay.remainingBudgetMillis(deadline, start)).isEqualTo(1000);
    assertThat(
            OutboxRelay.remainingBudgetMillis(deadline, start + Duration.ofMillis(400).toNanos()))
        .isEqualTo(600);
    assertThat(
            OutboxRelay.remainingBudgetMillis(deadline, start + Duration.ofMillis(999).toNanos()))
        .isEqualTo(1);
    // Spent budget clamps to an immediate poll rather than going negative.
    assertThat(OutboxRelay.remainingBudgetMillis(deadline, deadline)).isZero();
    assertThat(
            OutboxRelay.remainingBudgetMillis(deadline, start + Duration.ofMillis(1500).toNanos()))
        .isZero();
  }

  @Test
  void handlesAMixedBatchOfSuccessFailureAndPoisonRowsIndependently() {
    // Every other test claims a single row, where fire-then-await is indistinguishable from
    // send-and-await-immediately. This one claims three at once.
    OutboxEvent sent = pendingRow(1L, payloadJson(), 0);
    OutboxEvent rejected = pendingRow(2L, payloadJson(), 0);
    OutboxEvent poison = pendingRow(3L, "this is not json", 0);
    when(outboxRepository.claimPending(anyInt())).thenReturn(List.of(sent, rejected, poison));

    when(kafkaTemplate.send(eq(TOPIC), eq(sent.getPartitionKey()), any(ActivityCreatedEvent.class)))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    when(kafkaTemplate.send(
            eq(TOPIC), eq(rejected.getPartitionKey()), any(ActivityCreatedEvent.class)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

    relay.drain();

    assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(sent.getSentAt()).isNotNull();
    assertThat(sent.getAttempts()).isZero();

    assertThat(rejected.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(rejected.getAttempts()).isEqualTo(1);
    assertThat(rejected.getLastError()).contains("broker down");
    assertThat(rejected.getSentAt()).isNull();

    assertThat(poison.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(poison.getAttempts()).isEqualTo(1);
    assertThat(poison.getLastError()).isNotBlank();
    assertThat(poison.getSentAt()).isNull();

    // The poison row is never handed to Kafka: only the two deserializable rows are sent.
    verify(kafkaTemplate, never())
        .send(anyString(), eq(poison.getPartitionKey()), any(ActivityCreatedEvent.class));
    verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any(ActivityCreatedEvent.class));
    // One flush for the whole batch, not one per row — the point of firing before awaiting.
    verify(kafkaTemplate, times(1)).flush();

    verify(outboxRepository).saveAll(List.of(sent, rejected, poison));
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
