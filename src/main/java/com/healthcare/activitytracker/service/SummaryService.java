package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.InvalidRequestException;
import com.healthcare.activitytracker.model.dto.SummaryResponse;
import com.healthcare.activitytracker.repository.ActivityRepository;
import com.healthcare.activitytracker.repository.projection.ActivityTypeSummaryProjection;
import com.healthcare.activitytracker.repository.projection.DistanceStepsProjection;
import com.healthcare.activitytracker.repository.projection.SourceCountProjection;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummaryService {

  private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

  @Value("${app.summary.max-range-days:365}")
  private int maxRangeDays;

  private final ActivityRepository activityRepository;

  public SummaryService(ActivityRepository activityRepository) {
    this.activityRepository = activityRepository;
  }

  /**
   * Returns aggregated activity statistics for the given date range. Aggregations include totals by
   * source and activity type, overall distance/steps/calories, and the current streak.
   *
   * @param userId the authenticated user's ID
   * @param from inclusive start date
   * @param to inclusive end date
   * @param zone the user's timezone, used for streak boundary calculations
   * @return the aggregated summary
   * @throws InvalidRequestException if {@code from} is after {@code to}, or the range exceeds
   *     {@code app.summary.max-range-days}
   */
  @Transactional(readOnly = true)
  public SummaryResponse getSummary(UUID userId, LocalDate from, LocalDate to, ZoneId zone) {
    long rangeDays = ChronoUnit.DAYS.between(from, to);
    if (rangeDays < 0) {
      throw new InvalidRequestException("'from' date must not be after 'to' date");
    }
    if (rangeDays > maxRangeDays) {
      throw new InvalidRequestException(
          "Date range cannot exceed " + maxRangeDays + " days (requested " + rangeDays + ")");
    }
    LocalDateTime fromDt = from.atStartOfDay();
    LocalDateTime toDt = to.atTime(LocalTime.MAX);

    // DB-level aggregation by source
    List<SourceCountProjection> sourceRows =
        activityRepository.countBySourceInRange(userId, fromDt, toDt);
    Map<String, Long> bySource = new LinkedHashMap<>();
    for (SourceCountProjection row : sourceRows) {
      bySource.put(row.getSource().name(), row.getCount());
    }

    // DB-level aggregation by activity type + source
    List<ActivityTypeSummaryProjection> typeRows =
        activityRepository.summarizeByActivityTypeInRange(userId, fromDt, toDt);
    List<SummaryResponse.ActivityTypeSummary> byType = new ArrayList<>();
    long totalActivities = 0;
    long totalDuration = 0;
    double totalCalories = 0;

    for (ActivityTypeSummaryProjection row : typeRows) {
      byType.add(
          SummaryResponse.ActivityTypeSummary.builder()
              .type(row.getActivityType().name())
              .source(row.getSource().name())
              .count(row.getCount())
              .totalMinutes(row.getTotalMinutes())
              .totalCalories(row.getTotalCalories())
              .build());

      totalActivities += row.getCount();
      totalDuration += row.getTotalMinutes();
      totalCalories += row.getTotalCalories();
    }

    byType.sort(Comparator.comparingLong(SummaryResponse.ActivityTypeSummary::getCount).reversed());

    // DB-level aggregation for distance and steps
    List<DistanceStepsProjection> distStepsList =
        activityRepository.sumDistanceAndSteps(userId, fromDt, toDt);
    DistanceStepsProjection distanceSteps =
        (distStepsList != null && !distStepsList.isEmpty()) ? distStepsList.get(0) : null;
    double totalDistance =
        distanceSteps != null && distanceSteps.getTotalDistance() != null
            ? distanceSteps.getTotalDistance()
            : 0.0;
    long totalSteps =
        distanceSteps != null && distanceSteps.getTotalSteps() != null
            ? distanceSteps.getTotalSteps()
            : 0L;

    long daysBetween = ChronoUnit.DAYS.between(from, to) + 1;
    double averageDailyCalories = daysBetween > 0 ? totalCalories / daysBetween : 0;

    int streakDays = getCurrentStreak(userId, zone);

    log.debug(
        "Summary generated for user {} from {} to {}: {} activities",
        userId,
        from,
        to,
        totalActivities);

    return SummaryResponse.builder()
        .from(from)
        .to(to)
        .totalActivities(totalActivities)
        .totalDurationMinutes(totalDuration)
        .totalCaloriesBurned(totalCalories)
        .totalDistanceKm(totalDistance)
        .totalSteps(totalSteps)
        .streakDays(streakDays)
        .averageDailyCalories(averageDailyCalories)
        .bySource(bySource)
        .byActivityType(byType)
        .build();
  }

  /**
   * Returns today's activity summary in the user's local timezone.
   *
   * @param userId the authenticated user's ID
   * @param zone the user's timezone
   * @return today's aggregated summary
   */
  public SummaryResponse getDailySummary(UUID userId, ZoneId zone) {
    LocalDate today = LocalDate.now(zone);
    return getSummary(userId, today, today, zone);
  }

  /**
   * Returns a week-to-date activity summary, from Monday of the current week through today, in the
   * user's local timezone.
   *
   * @param userId the authenticated user's ID
   * @param zone the user's timezone
   * @return the current week's aggregated summary
   */
  public SummaryResponse getWeeklySummary(UUID userId, ZoneId zone) {
    LocalDate today = LocalDate.now(zone);
    LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
    return getSummary(userId, weekStart, today, zone);
  }

  /**
   * Returns a month-to-date activity summary, from the first of the current month through today, in
   * the user's local timezone.
   *
   * @param userId the authenticated user's ID
   * @param zone the user's timezone
   * @return the current month's aggregated summary
   */
  public SummaryResponse getMonthlySummary(UUID userId, ZoneId zone) {
    LocalDate today = LocalDate.now(zone);
    LocalDate monthStart = today.withDayOfMonth(1);
    return getSummary(userId, monthStart, today, zone);
  }

  /**
   * Calculates the current activity streak in the user's local timezone. Without a zone,
   * server-midnight boundaries would be used, breaking streaks for users in timezones that differ
   * significantly from the server's.
   */
  @Transactional(readOnly = true)
  public int getCurrentStreak(UUID userId, ZoneId zone) {
    LocalDateTime since = LocalDate.now(zone).minusDays(400).atStartOfDay();
    List<LocalDate> activeDates = activityRepository.findDistinctActiveDatesByUserId(userId, since);
    if (activeDates == null || activeDates.isEmpty()) {
      return 0;
    }

    Set<LocalDate> dateSet = new HashSet<>(activeDates);
    LocalDate cursor = LocalDate.now(zone);

    // If today has no activity yet, start streak check from yesterday
    if (!dateSet.contains(cursor)) {
      cursor = cursor.minusDays(1);
    }

    int streak = 0;
    while (dateSet.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    return streak;
  }
}
