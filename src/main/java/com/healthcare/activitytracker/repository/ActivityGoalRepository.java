package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.ActivityGoal;
import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.model.enums.GoalPeriod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityGoalRepository extends JpaRepository<ActivityGoal, UUID> {

  /** A user's live goals, oldest first so the dashboard ordering is stable across reloads. */
  List<ActivityGoal> findByUserIdAndActiveTrueOrderByCreatedAtAsc(UUID userId);

  /**
   * Scoped by user in the query rather than checked after loading — the same device {@link
   * ActivityRepository#findByIdAndUserId} uses to keep one user from reaching another's rows.
   */
  Optional<ActivityGoal> findByIdAndUserIdAndActiveTrue(UUID id, UUID userId);

  boolean existsByUserIdAndMetricAndPeriodAndActiveTrue(
      UUID userId, GoalMetric metric, GoalPeriod period);

  long countByUserIdAndActiveTrue(UUID userId);

  /**
   * Hard-deletes every goal for a user, including archived ones (account deletion). Distinct from
   * the soft archive a single goal gets: here the parent user row is going away, so the FK leaves
   * no choice.
   */
  @Modifying
  @Query("DELETE FROM ActivityGoal g WHERE g.user.id = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);
}
