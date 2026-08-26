package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.activitytracker.model.entity.OutboxEvent;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.model.enums.OutboxStatus;
import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import com.healthcare.activitytracker.repository.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityEventPublisherTest {

  private static final String TOPIC = "activity-events";

  @Mock private OutboxEventRepository outboxRepository;

  private ObjectMapper objectMapper;
  private ActivityEventPublisher publisher;

  private final UUID userId = UUID.randomUUID();
  private final UUID activityId = UUID.randomUUID();

  private ActivityCreatedEvent event;

  @BeforeEach
  void setUp() {
    // ActivityCreatedEvent carries LocalDateTime fields; a bare ObjectMapper cannot
    // serialize them. Production injects Spring Boot's mapper, which has this module.
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    publisher = new ActivityEventPublisher(outboxRepository, objectMapper, TOPIC);

    event =
        ActivityCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("ACTIVITY_CREATED")
            .occurredAt(LocalDateTime.now())
            .activityId(activityId)
            .userId(userId)
            .activityType(ActivityType.RUNNING)
            .source(ActivitySource.MANUAL)
            .durationMinutes(30)
            .caloriesBurned(250.0)
            .distanceKm(5.0)
            .startedAt(LocalDateTime.now())
            .build();
  }

  private OutboxEvent captureSavedRow() {
    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void writesAnOutboxRowWithTheRoutingFieldsPopulated() {
    publisher.publishActivityCreated(event);

    OutboxEvent saved = captureSavedRow();
    assertThat(saved.getEventId()).isEqualTo(event.getEventId());
    assertThat(saved.getAggregateId()).isEqualTo(activityId);
    assertThat(saved.getPartitionKey()).isEqualTo(userId.toString());
    assertThat(saved.getTopic()).isEqualTo(TOPIC);
    assertThat(saved.getEventType()).isEqualTo("ACTIVITY_CREATED");
  }

  @Test
  void writesTheRowAsPendingWithZeroAttempts() {
    publisher.publishActivityCreated(event);

    OutboxEvent saved = captureSavedRow();
    assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(saved.getAttempts()).isZero();
    assertThat(saved.getSentAt()).isNull();
    assertThat(saved.getLastError()).isNull();
  }

  @Test
  void payloadRoundTripsBackToAnEqualEvent() throws Exception {
    publisher.publishActivityCreated(event);

    OutboxEvent saved = captureSavedRow();
    ActivityCreatedEvent restored =
        objectMapper.readValue(saved.getPayload(), ActivityCreatedEvent.class);
    assertThat(restored).isEqualTo(event);
  }

  @Test
  void neverTouchesKafkaDirectly() {
    // Regression guard for the race this design removes: the publisher must have no
    // transport dependency at all, so nothing can reach a broker before commit.
    assertThat(ActivityEventPublisher.class.getDeclaredFields())
        .noneMatch(field -> field.getType().getName().contains("kafka"));
  }

  @Test
  void propagatesRepositoryFailureInsteadOfSwallowingIt() {
    // The old publisher swallowed everything to protect the request path. An outbox insert
    // must NOT be swallowed: committing an activity without its event reintroduces the
    // silent loss this design exists to remove.
    doThrow(new RuntimeException("db down")).when(outboxRepository).save(any());

    assertThatThrownBy(() -> publisher.publishActivityCreated(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db down");
  }
}
