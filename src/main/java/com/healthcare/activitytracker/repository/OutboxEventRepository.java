package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.OutboxEvent;
import com.healthcare.activitytracker.model.enums.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  /**
   * Selects and row-locks the oldest pending events.
   *
   * <p>There is no {@code CLAIMED} status — the lock held for the duration of the caller's
   * transaction <em>is</em> the claim. That is what makes a crash mid-drain self-healing: the lock
   * dies with the connection and the rows are simply still {@code PENDING} on the next poll.
   *
   * <p>{@code SKIP LOCKED} is close to a no-op with a single instance and a single scheduler
   * thread. It costs nothing and means scaling out later cannot double-publish.
   *
   * <p>Rows whose retry backoff has not elapsed are skipped. {@code next_attempt_at IS NULL} means
   * "eligible now" and covers every first attempt as well as every row written before V4. The
   * comparison is against {@code now() AT TIME ZONE 'UTC'} because the column is {@code TIMESTAMP}
   * without a zone and the relay writes {@code LocalDateTime.now(ZoneOffset.UTC)} into it; a bare
   * {@code now()} would be interpreted in the database session's zone instead.
   *
   * <p>Ordering stays on {@code id}, not {@code next_attempt_at}, to preserve the near-FIFO drain
   * among rows that are eligible.
   *
   * <p>PostgreSQL-only syntax. H2 cannot run this, which is why it is covered by {@code
   * OutboxEventRepositoryPostgresTest} rather than the main suite. Native queries are not validated
   * at context startup, so its presence does not break the H2 slices.
   */
  @Query(
      value =
          """
          SELECT * FROM activity_outbox
          WHERE status = 'PENDING'
            AND (next_attempt_at IS NULL OR next_attempt_at <= (now() AT TIME ZONE 'UTC'))
          ORDER BY id
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<OutboxEvent> claimPending(@Param("limit") int limit);

  /** Deletes published rows past the retention window. Returns the number deleted. */
  @Modifying
  @Query("DELETE FROM OutboxEvent o WHERE o.status = :status AND o.sentAt < :cutoff")
  int purgeSentBefore(@Param("status") OutboxStatus status, @Param("cutoff") LocalDateTime cutoff);
}
