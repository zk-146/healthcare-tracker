package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.Activity;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.repository.projection.ActivityTypeSummaryProjection;
import com.healthcare.activitytracker.repository.projection.DistanceStepsProjection;
import com.healthcare.activitytracker.repository.projection.SourceCountProjection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityRepository
    extends JpaRepository<Activity, UUID>, JpaSpecificationExecutor<Activity> {

  Optional<Activity> findByIdAndUserId(UUID id, UUID userId);

  /** True if a workout from the given external source record has already been imported. */
  boolean existsByUserIdAndExternalId(UUID userId, String externalId);

  /** Delete all activities for a user (account deletion). */
  @Modifying
  @Query("DELETE FROM Activity a WHERE a.user.id = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);

  /**
   * The CASTs are load-bearing on PostgreSQL. A parameter whose only appearance is {@code ? IS
   * NULL} gives the planner nothing to infer a type from, and Postgres rejects the statement with
   * "could not determine data type of parameter". The comparison arms need no cast — the column
   * supplies the type there. H2 infers either way, so this only ever failed against real Postgres.
   */
  @Query(
      "SELECT a FROM Activity a WHERE a.user.id = :userId "
          + "AND (CAST(:from AS LocalDateTime) IS NULL OR a.startedAt >= :from) "
          + "AND (CAST(:to AS LocalDateTime) IS NULL OR a.startedAt <= :to) "
          + "AND (CAST(:activityType AS String) IS NULL OR a.activityType = :activityType) "
          + "AND (CAST(:source AS String) IS NULL OR a.source = :source) "
          + "ORDER BY a.startedAt DESC")
  Page<Activity> findByFilters(
      @Param("userId") UUID userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("activityType") ActivityType activityType,
      @Param("source") ActivitySource source,
      Pageable pageable);

  @Query(
      "SELECT a.source as source, COUNT(a) as count FROM Activity a "
          + "WHERE a.user.id = :userId AND a.startedAt >= :from AND a.startedAt <= :to "
          + "GROUP BY a.source")
  List<SourceCountProjection> countBySourceInRange(
      @Param("userId") UUID userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      "SELECT a.activityType as activityType, a.source as source, COUNT(a) as count, "
          + "COALESCE(SUM(a.durationMinutes), 0) as totalMinutes, "
          + "COALESCE(SUM(a.caloriesBurned), 0) as totalCalories "
          + "FROM Activity a WHERE a.user.id = :userId "
          + "AND a.startedAt >= :from AND a.startedAt <= :to "
          + "GROUP BY a.activityType, a.source")
  List<ActivityTypeSummaryProjection> summarizeByActivityTypeInRange(
      @Param("userId") UUID userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      "SELECT COALESCE(SUM(a.distanceKm), 0) as totalDistance, COALESCE(SUM(a.steps), 0) as totalSteps "
          + "FROM Activity a WHERE a.user.id = :userId "
          + "AND a.startedAt >= :from AND a.startedAt <= :to")
  List<DistanceStepsProjection> sumDistanceAndSteps(
      @Param("userId") UUID userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      "SELECT DISTINCT CAST(a.startedAt AS java.time.LocalDate) FROM Activity a "
          + "WHERE a.user.id = :userId AND a.startedAt >= :since ORDER BY 1 DESC")
  List<LocalDate> findDistinctActiveDatesByUserId(
      @Param("userId") UUID userId, @Param("since") LocalDateTime since);
}
