package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.FieldValidationException;
import com.healthcare.activitytracker.exception.ResourceConflictException;
import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.GoalRequest;
import com.healthcare.activitytracker.model.dto.GoalResponse;
import com.healthcare.activitytracker.model.dto.GoalUpdateRequest;
import com.healthcare.activitytracker.model.dto.SummaryResponse;
import com.healthcare.activitytracker.model.entity.ActivityGoal;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.model.enums.GoalPeriod;
import com.healthcare.activitytracker.repository.ActivityGoalRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import com.healthcare.activitytracker.validation.GoalTargetValidator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User-defined activity goals and the user's standing against them.
 *
 * <p>Deliberately owns no aggregation of its own: every metric a goal can measure is already
 * produced by {@link SummaryService}, and reusing it means a goal's notion of "this week" is the
 * same one the rest of the app uses rather than a second, subtly different definition.
 */
@Service
public class GoalService {

  /**
   * Ceiling on live goals per user. Two reasons, both real: it bounds the summary work {@link
   * #getGoals} performs, and a wall of competing targets is itself a known driver of disengagement
   * — a goal list is only useful while it is short enough to act on.
   */
  static final int MAX_ACTIVE_GOALS = 10;

  private final ActivityGoalRepository goalRepository;
  private final UserRepository userRepository;
  private final SummaryService summaryService;

  public GoalService(
      ActivityGoalRepository goalRepository,
      UserRepository userRepository,
      SummaryService summaryService) {
    this.goalRepository = goalRepository;
    this.userRepository = userRepository;
    this.summaryService = summaryService;
  }

  /**
   * Every live goal with its current standing, oldest first.
   *
   * @param userId the authenticated user's ID
   * @param zone the caller's timezone, which decides where each period starts and ends
   */
  @Transactional(readOnly = true)
  public List<GoalResponse> getGoals(UUID userId, ZoneId zone) {
    List<ActivityGoal> goals = goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId);

    // One summary per distinct period, not per goal. Three weekly goals must cost a single
    // summary: getSummary recomputes the streak on every call, which its own javadoc flags
    // as scanning up to ~400 rows.
    Map<GoalPeriod, SummaryResponse> summaries = new EnumMap<>(GoalPeriod.class);
    List<GoalResponse> responses = new ArrayList<>(goals.size());
    for (ActivityGoal goal : goals) {
      SummaryResponse summary =
          summaries.computeIfAbsent(goal.getPeriod(), period -> summaryFor(userId, period, zone));
      responses.add(toResponse(goal, summary));
    }
    return responses;
  }

  /**
   * Creates a goal.
   *
   * @throws ResourceConflictException if a live goal already covers this metric and period, or the
   *     user is at {@link #MAX_ACTIVE_GOALS}
   */
  @Transactional
  public GoalResponse createGoal(UUID userId, GoalRequest request, ZoneId zone) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (goalRepository.existsByUserIdAndMetricAndPeriodAndActiveTrue(
        userId, request.getMetric(), request.getPeriod())) {
      throw new ResourceConflictException(
          "A "
              + request.getPeriod().name().toLowerCase(Locale.ROOT)
              + " "
              + request.getMetric().getUnit()
              + " goal already exists. Change its target or remove it first.");
    }

    if (goalRepository.countByUserIdAndActiveTrue(userId) >= MAX_ACTIVE_GOALS) {
      throw new ResourceConflictException(
          "You already have the maximum of " + MAX_ACTIVE_GOALS + " active goals.");
    }

    ActivityGoal saved =
        goalRepository.save(
            ActivityGoal.builder()
                .user(user)
                .metric(request.getMetric())
                .period(request.getPeriod())
                .targetValue(request.getTargetValue())
                .active(true)
                .build());

    return toResponse(saved, summaryFor(userId, saved.getPeriod(), zone));
  }

  /**
   * Changes a goal's target. Metric and period are immutable — they key the active-goal uniqueness
   * index, so changing either means retiring this goal and creating another.
   *
   * @throws ResourceNotFoundException if the goal does not exist, is retired, or belongs to someone
   *     else
   * @throws FieldValidationException if the target is out of range for the stored metric
   */
  @Transactional
  public GoalResponse updateGoal(UUID userId, UUID goalId, GoalUpdateRequest request, ZoneId zone) {
    ActivityGoal goal =
        goalRepository
            .findByIdAndUserIdAndActiveTrue(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));

    // The bound depends on the stored metric, which no ConstraintValidator on the request can
    // see. Same rule function as the create path so the two cannot drift apart.
    String violation =
        GoalTargetValidator.messageFor(
            request.getTargetValue(), goal.getMetric(), goal.getPeriod());
    if (violation != null) {
      throw new FieldValidationException("targetValue", violation);
    }

    goal.setTargetValue(request.getTargetValue());
    ActivityGoal saved = goalRepository.save(goal);
    return toResponse(saved, summaryFor(userId, saved.getPeriod(), zone));
  }

  /**
   * Retires a goal. A soft archive, not a delete: uniqueness is scoped to active rows, so this is
   * what frees the metric/period slot for a new goal while leaving a record that this one existed.
   *
   * @throws ResourceNotFoundException if the goal does not exist, is already retired, or belongs to
   *     someone else
   */
  @Transactional
  public void deleteGoal(UUID userId, UUID goalId) {
    ActivityGoal goal =
        goalRepository
            .findByIdAndUserIdAndActiveTrue(goalId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
    goal.setActive(false);
    goalRepository.save(goal);
  }

  private SummaryResponse summaryFor(UUID userId, GoalPeriod period, ZoneId zone) {
    return switch (period) {
      case DAILY -> summaryService.getDailySummary(userId, zone);
      case WEEKLY -> summaryService.getWeeklySummary(userId, zone);
      case MONTHLY -> summaryService.getMonthlySummary(userId, zone);
    };
  }

  /** Pulls the one aggregate this goal measures out of the period summary. */
  static double currentValue(GoalMetric metric, SummaryResponse summary) {
    return switch (metric) {
      case MINUTES -> (double) summary.getTotalDurationMinutes();
      case SESSIONS -> (double) summary.getTotalActivities();
      case DISTANCE_KM -> summary.getTotalDistanceKm();
      case STEPS -> (double) summary.getTotalSteps();
      case CALORIES -> summary.getTotalCaloriesBurned();
    };
  }

  /**
   * Full length of the period in days — the denominator for pace, and distinct from how much of it
   * has elapsed. February and July give different monthly denominators, which is correct: a monthly
   * target is spread over the month actually being lived.
   */
  static int daysInPeriod(GoalPeriod period, LocalDate today) {
    return switch (period) {
      case DAILY -> 1;
      case WEEKLY -> 7;
      case MONTHLY -> today.lengthOfMonth();
    };
  }

  private GoalResponse toResponse(ActivityGoal goal, SummaryResponse summary) {
    return GoalResponse.builder()
        .id(goal.getId())
        .metric(goal.getMetric().name())
        .period(goal.getPeriod().name())
        .targetValue(goal.getTargetValue())
        .unit(goal.getMetric().getUnit())
        .createdAt(goal.getCreatedAt())
        .updatedAt(goal.getUpdatedAt())
        .progress(buildProgress(goal, summary))
        .build();
  }

  private GoalResponse.Progress buildProgress(ActivityGoal goal, SummaryResponse summary) {
    double target = goal.getTargetValue();
    double current = currentValue(goal.getMetric(), summary);

    LocalDate from = summary.getFrom();
    LocalDate to = summary.getTo();
    int daysTotal = daysInPeriod(goal.getPeriod(), to);
    int daysElapsed = (int) ChronoUnit.DAYS.between(from, to) + 1;
    int daysRemaining = Math.max(0, daysTotal - daysElapsed);

    // Guarded despite validation forbidding a non-positive target on both write paths: a row
    // written around the API would otherwise put Infinity or NaN into the JSON response.
    boolean scorable = target > 0;
    double expectedByNow = scorable ? target * daysElapsed / daysTotal : 0;
    double percentComplete = scorable ? current / target * 100 : 0;
    boolean achieved = scorable && current >= target;

    return GoalResponse.Progress.builder()
        .from(from)
        .to(to)
        .currentValue(round(current))
        .percentComplete(round(percentComplete))
        .achieved(achieved)
        .daysElapsed(daysElapsed)
        .daysTotal(daysTotal)
        .daysRemaining(daysRemaining)
        .expectedByNow(round(expectedByNow))
        .onTrack(achieved || (scorable && current >= expectedByNow))
        .build();
  }

  /** One decimal place, matching how CalorieEstimator rounds its own output. */
  private static double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
