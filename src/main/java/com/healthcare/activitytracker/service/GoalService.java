package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.GoalRequest;
import com.healthcare.activitytracker.model.dto.GoalResponse;
import com.healthcare.activitytracker.model.dto.SummaryResponse;
import com.healthcare.activitytracker.model.entity.Goal;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.repository.GoalRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD operations and daily-goal evaluation for user-defined fitness goals. */
@Service
public class GoalService {

  private static final Logger log = LoggerFactory.getLogger(GoalService.class);

  private final GoalRepository goalRepository;
  private final UserRepository userRepository;
  private final SummaryService summaryService;
  private final NotificationService notificationService;

  public GoalService(
      GoalRepository goalRepository,
      UserRepository userRepository,
      SummaryService summaryService,
      NotificationService notificationService) {
    this.goalRepository = goalRepository;
    this.userRepository = userRepository;
    this.summaryService = summaryService;
    this.notificationService = notificationService;
  }

  /**
   * Creates a new active goal for the authenticated user.
   *
   * @param userId the owner's ID
   * @param request metric + targetValue
   * @return the persisted goal as a DTO
   */
  @Transactional
  public GoalResponse create(UUID userId, GoalRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Goal goal =
        Goal.builder().user(user).metric(request.getMetric()).targetValue(request.getTargetValue()).build();

    return toResponse(goalRepository.save(goal));
  }

  /**
   * Returns all active goals for the given user.
   *
   * @param userId the owner's ID
   * @return list of active goal DTOs
   */
  @Transactional(readOnly = true)
  public List<GoalResponse> listActive(UUID userId) {
    return goalRepository.findByUserIdAndActiveTrue(userId).stream().map(this::toResponse).toList();
  }

  /**
   * Returns a single goal by ID, scoped to the requesting user.
   *
   * @param userId the owner's ID
   * @param goalId the goal to retrieve
   * @return the goal DTO
   * @throws ResourceNotFoundException if not found or belongs to another user
   */
  @Transactional(readOnly = true)
  public GoalResponse getById(UUID userId, UUID goalId) {
    return toResponse(findOwned(userId, goalId));
  }

  /**
   * Updates the target value of an existing goal.
   *
   * @param userId the owner's ID
   * @param goalId the goal to update
   * @param request new targetValue (metric cannot change)
   * @return the updated goal DTO
   * @throws ResourceNotFoundException if not found or belongs to another user
   */
  @Transactional
  public GoalResponse update(UUID userId, UUID goalId, GoalRequest request) {
    Goal goal = findOwned(userId, goalId);
    goal.setTargetValue(request.getTargetValue());
    return toResponse(goalRepository.save(goal));
  }

  /**
   * Soft-deletes a goal by setting {@code active = false}.
   *
   * @param userId the owner's ID
   * @param goalId the goal to deactivate
   * @throws ResourceNotFoundException if not found or belongs to another user
   */
  @Transactional
  public void deactivate(UUID userId, UUID goalId) {
    Goal goal = findOwned(userId, goalId);
    goal.setActive(false);
    goalRepository.save(goal);
  }

  /**
   * Evaluates each active goal for the user against today's summary. For any goal where the
   * current metric value meets or exceeds the target — and the goal has not already fired today —
   * stamps {@code lastAchievedDate} and dispatches a notification.
   *
   * <p>Called asynchronously from {@link ActivityEventConsumer} after every new activity.
   *
   * @param userId the user whose goals to evaluate
   * @param zone the user's timezone (used to determine "today")
   */
  @Transactional
  public void evaluateGoals(UUID userId, ZoneId zone) {
    List<Goal> activeGoals = goalRepository.findByUserIdAndActiveTrue(userId);
    if (activeGoals.isEmpty()) {
      return;
    }

    LocalDate today = LocalDate.now(zone);
    SummaryResponse summary = summaryService.getDailySummary(userId, zone);

    User user = null; // load lazily — only if a goal is actually achieved

    for (Goal goal : activeGoals) {
      if (today.equals(goal.getLastAchievedDate())) {
        // already fired today
        continue;
      }

      double current = metricValue(summary, goal.getMetric());
      if (current < goal.getTargetValue()) {
        continue;
      }

      // Goal met for the first time today
      goal.setLastAchievedDate(today);
      goalRepository.save(goal);

      if (user == null) {
        user =
            userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("User vanished: " + userId));
      }

      log.info(
          "GOAL ACHIEVED userId={} metric={} target={} current={}",
          userId,
          goal.getMetric(),
          goal.getTargetValue(),
          current);

      notificationService.notifyGoalAchieved(user, goal);
    }
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private Goal findOwned(UUID userId, UUID goalId) {
    return goalRepository
        .findByIdAndUserId(goalId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
  }

  private double metricValue(SummaryResponse summary, GoalMetric metric) {
    return switch (metric) {
      case CALORIES_BURNED -> summary.getTotalCaloriesBurned();
      case DURATION_MINUTES -> summary.getTotalDurationMinutes();
      case ACTIVITY_COUNT -> summary.getTotalActivities();
      case DISTANCE_KM -> summary.getTotalDistanceKm();
      case STEPS -> summary.getTotalSteps();
    };
  }

  private GoalResponse toResponse(Goal goal) {
    return GoalResponse.builder()
        .id(goal.getId())
        .metric(goal.getMetric())
        .targetValue(goal.getTargetValue())
        .active(goal.isActive())
        .lastAchievedDate(goal.getLastAchievedDate())
        .createdAt(goal.getCreatedAt())
        .updatedAt(goal.getUpdatedAt())
        .build();
  }
}
