package com.healthcare.activitytracker.model.entity;

import com.healthcare.activitytracker.model.enums.OutboxStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One pending or completed domain event, written in the same transaction as the aggregate it
 * describes and published later by {@code OutboxRelay}.
 *
 * <p>Intentionally has no foreign key to {@code activities}: the outbox is a transport log, not a
 * relationship. A row must remain publishable even if its activity is deleted before the relay gets
 * to it.
 */
@Entity
@Table(
    name = "activity_outbox",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "activity_outbox_event_id_key",
          columnNames = {"event_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

  /**
   * Monotonic surrogate key. Deliberately a sequence rather than a UUID: the relay drains {@code
   * ORDER BY id} and needs an ordering it can rely on.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Idempotency key carried in the payload. Unique so a double insert cannot happen. */
  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  /** The activity this event describes. */
  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  /** Kafka message key — the userId string, preserving per-user partitioning. */
  @Column(name = "partition_key", nullable = false, length = 64)
  private String partitionKey;

  @Column(name = "topic", nullable = false)
  private String topic;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  /**
   * Jackson-serialized event. {@code columnDefinition} is required: Hibernate would otherwise
   * expect {@code varchar(255)} and {@code ddl-auto: validate} would reject the migration's TEXT.
   */
  @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  @Builder.Default
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(name = "attempts", nullable = false)
  @Builder.Default
  private Integer attempts = 0;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  /**
   * Earliest time the relay may retry this row after a failure. {@code null} means eligible now,
   * which is the state of every freshly inserted row and of every row written before V4.
   *
   * <p>Set by {@code OutboxRelay} to an exponentially growing offset so a Kafka outage lasting
   * longer than {@code maxAttempts} poll intervals does not park every affected row as FAILED.
   */
  @Column(name = "next_attempt_at")
  private LocalDateTime nextAttemptAt;
}
