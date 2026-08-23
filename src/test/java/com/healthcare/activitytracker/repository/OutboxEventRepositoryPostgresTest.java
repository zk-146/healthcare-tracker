package com.healthcare.activitytracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.entity.OutboxEvent;
import com.healthcare.activitytracker.model.enums.OutboxStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Outbox repository tests against a real PostgreSQL instance (Testcontainers).
 *
 * <p>{@code claimPending} uses {@code FOR UPDATE SKIP LOCKED} and the V3 migration declares a
 * partial index — neither exists on H2, so this is the only place they run at all. It also
 * exercises V3 for real and, via {@code ddl-auto: validate} inherited from the default profile,
 * proves the migration matches {@link OutboxEvent}.
 *
 * <p><strong>Not covered here:</strong> that two concurrent claims return disjoint rows. Doing that
 * honestly needs a second connection driving its own transaction from another thread, and the
 * property is not load-bearing today — the application runs as a single instance with one scheduler
 * thread. What is verified is that the statement parses and executes on PostgreSQL, which is the
 * actual risk H2 hides.
 *
 * <p>Skipped automatically when Docker is unavailable; runs in CI.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OutboxEventRepositoryPostgresTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private OutboxEventRepository outboxRepository;

  @BeforeEach
  void setUp() {
    outboxRepository.deleteAll();
  }

  private OutboxEvent row(OutboxStatus status, LocalDateTime sentAt) {
    return OutboxEvent.builder()
        .eventId(UUID.randomUUID())
        .aggregateId(UUID.randomUUID())
        .partitionKey(UUID.randomUUID().toString())
        .topic("activity-events")
        .eventType("ACTIVITY_CREATED")
        .payload("{\"eventType\":\"ACTIVITY_CREATED\"}")
        .status(status)
        .attempts(0)
        .sentAt(sentAt)
        .build();
  }

  @Test
  void claimPendingReturnsOnlyPendingRowsInIdOrder() {
    OutboxEvent first = outboxRepository.save(row(OutboxStatus.PENDING, null));
    outboxRepository.save(row(OutboxStatus.SENT, LocalDateTime.now(ZoneOffset.UTC)));
    OutboxEvent third = outboxRepository.save(row(OutboxStatus.PENDING, null));
    outboxRepository.save(row(OutboxStatus.FAILED, null));

    List<OutboxEvent> claimed = outboxRepository.claimPending(10);

    assertThat(claimed)
        .extracting(OutboxEvent::getId)
        .containsExactly(first.getId(), third.getId());
  }

  @Test
  void claimPendingRespectsTheLimit() {
    outboxRepository.save(row(OutboxStatus.PENDING, null));
    outboxRepository.save(row(OutboxStatus.PENDING, null));
    outboxRepository.save(row(OutboxStatus.PENDING, null));

    assertThat(outboxRepository.claimPending(2)).hasSize(2);
  }

  @Test
  void claimPendingReturnsEmptyWhenNothingIsPending() {
    outboxRepository.save(row(OutboxStatus.SENT, LocalDateTime.now(ZoneOffset.UTC)));

    assertThat(outboxRepository.claimPending(10)).isEmpty();
  }

  @Test
  void purgeSentBeforeDeletesOnlyOldSentRows() {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    outboxRepository.save(row(OutboxStatus.SENT, now.minusDays(30)));
    OutboxEvent recentlySent = outboxRepository.save(row(OutboxStatus.SENT, now.minusHours(1)));
    OutboxEvent stillPending = outboxRepository.save(row(OutboxStatus.PENDING, null));

    int deleted = outboxRepository.purgeSentBefore(OutboxStatus.SENT, now.minusDays(7));

    assertThat(deleted).isEqualTo(1);
    assertThat(outboxRepository.findAll())
        .extracting(OutboxEvent::getId)
        .containsExactlyInAnyOrder(recentlySent.getId(), stillPending.getId());
  }
}
