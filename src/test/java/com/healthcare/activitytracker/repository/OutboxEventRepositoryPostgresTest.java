package com.healthcare.activitytracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.entity.OutboxEvent;
import com.healthcare.activitytracker.model.enums.OutboxStatus;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
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

  /**
   * {@code preferQueryMode=simple} is carried deliberately: it is what production runs (see the
   * datasource URL in {@code application.yml}) and nothing else in the suite exercises it. {@link
   * OutboxEvent} uses {@code GenerationType.IDENTITY}, so the driver mode is load-bearing for every
   * insert here.
   *
   * <p>The parameter is attached with {@code withUrlParam} rather than a
   * {@code @DynamicPropertySource} override of {@code spring.datasource.url}, because
   * {@code @ServiceConnection} contributes a {@code JdbcConnectionDetails} bean that takes
   * precedence over the property — the override would be silently ignored. {@code withUrlParam}
   * feeds the container's own {@code getJdbcUrl()}, which is what those connection details return.
   */
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine").withUrlParam("preferQueryMode", "simple");

  @Autowired private OutboxEventRepository outboxRepository;
  @Autowired private DataSource dataSource;

  @BeforeEach
  void setUp() {
    outboxRepository.deleteAll();
  }

  private OutboxEvent row(OutboxStatus status, LocalDateTime sentAt) {
    return row(status, sentAt, null);
  }

  private OutboxEvent row(OutboxStatus status, LocalDateTime sentAt, LocalDateTime nextAttemptAt) {
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
        .nextAttemptAt(nextAttemptAt)
        .build();
  }

  @Test
  void connectsWithTheSameDriverModeProductionUses() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getURL()).contains("preferQueryMode=simple");
    }
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
  void claimPendingSkipsRowsWhoseBackoffHasNotElapsed() {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    OutboxEvent notYetDue =
        outboxRepository.save(row(OutboxStatus.PENDING, null, now.plusMinutes(5)));
    OutboxEvent backoffElapsed =
        outboxRepository.save(row(OutboxStatus.PENDING, null, now.minusMinutes(5)));
    OutboxEvent neverAttempted = outboxRepository.save(row(OutboxStatus.PENDING, null, null));

    List<OutboxEvent> claimed = outboxRepository.claimPending(10);

    assertThat(claimed)
        .extracting(OutboxEvent::getId)
        .containsExactly(backoffElapsed.getId(), neverAttempted.getId())
        .doesNotContain(notYetDue.getId());
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
