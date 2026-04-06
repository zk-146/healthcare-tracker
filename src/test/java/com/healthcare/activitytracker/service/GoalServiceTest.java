package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

  @Mock private GoalRepository goalRepository;
  @Mock private UserRepository userRepository;
  @Mock private SummaryService summaryService;
  @Mock private NotificationService notificationService;

  private GoalService goalService;

  private final UUID userId = UUID.randomUUID();
  private User user;

  @BeforeEach
  void setUp() {
    goalService = new GoalService(goalRepository, userRepository, summaryService, notificationService);
    user = User.builder().id(userId).email("user@example.com").fullName("Test User").build();
  }

  // ── create ────────────────────────────────────────────────────────────────

  @Test
  void create_savesGoalWithCorrectFields() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    GoalRequest request = GoalRequest.builder().metric(GoalMetric.CALORIES_BURNED).targetValue(500).build();

    Goal saved = Goal.builder().id(UUID.randomUUID()).user(user)
        .metric(GoalMetric.CALORIES_BURNED).targetValue(500).active(true).build();
    when(goalRepository.save(any())).thenReturn(saved);

    GoalResponse response = goalService.create(userId, request);

    ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
    verify(goalRepository).save(captor.capture());
    assertThat(captor.getValue().getMetric()).isEqualTo(GoalMetric.CALORIES_BURNED);
    assertThat(captor.getValue().getTargetValue()).isEqualTo(500);
    assertThat(captor.getValue().isActive()).isTrue();
    assertThat(response.getMetric()).isEqualTo(GoalMetric.CALORIES_BURNED);
  }

  @Test
  void create_throwsNotFound_whenUserMissing() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());
    GoalRequest request = GoalRequest.builder().metric(GoalMetric.STEPS).targetValue(10000).build();

    assertThatThrownBy(() -> goalService.create(userId, request))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(goalRepository, never()).save(any());
  }

  // ── listActive ────────────────────────────────────────────────────────────

  @Test
  void listActive_returnsMappedGoals() {
    Goal g = Goal.builder().id(UUID.randomUUID()).user(user)
        .metric(GoalMetric.DURATION_MINUTES).targetValue(30).active(true).build();
    when(goalRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(g));

    List<GoalResponse> result = goalService.listActive(userId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getMetric()).isEqualTo(GoalMetric.DURATION_MINUTES);
    assertThat(result.get(0).isActive()).isTrue();
  }

  // ── getById ───────────────────────────────────────────────────────────────

  @Test
  void getById_returnsGoal_whenOwned() {
    UUID goalId = UUID.randomUUID();
    Goal g = Goal.builder().id(goalId).user(user)
        .metric(GoalMetric.DISTANCE_KM).targetValue(5.0).active(true).build();
    when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(g));

    GoalResponse result = goalService.getById(userId, goalId);

    assertThat(result.getId()).isEqualTo(goalId);
    assertThat(result.getMetric()).isEqualTo(GoalMetric.DISTANCE_KM);
  }

  @Test
  void getById_throwsNotFound_whenMissing() {
    UUID goalId = UUID.randomUUID();
    when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> goalService.getById(userId, goalId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // ── update ────────────────────────────────────────────────────────────────

  @Test
  void update_changesTargetValue() {
    UUID goalId = UUID.randomUUID();
    Goal g = Goal.builder().id(goalId).user(user)
        .metric(GoalMetric.STEPS).targetValue(5000).active(true).build();
    when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(g));
    when(goalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    GoalResponse result = goalService.update(userId, goalId,
        GoalRequest.builder().metric(GoalMetric.STEPS).targetValue(10000).build());

    assertThat(result.getTargetValue()).isEqualTo(10000);
    verify(goalRepository).save(g);
  }

  // ── deactivate ────────────────────────────────────────────────────────────

  @Test
  void deactivate_setsActiveFalse() {
    UUID goalId = UUID.randomUUID();
    Goal g = Goal.builder().id(goalId).user(user)
        .metric(GoalMetric.ACTIVITY_COUNT).targetValue(2).active(true).build();
    when(goalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(g));

    goalService.deactivate(userId, goalId);

    assertThat(g.isActive()).isFalse();
    verify(goalRepository).save(g);
  }

  // ── evaluateGoals ─────────────────────────────────────────────────────────

  @Test
  void evaluateGoals_doesNothing_whenNoActiveGoals() {
    when(goalRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());

    goalService.evaluateGoals(userId, ZoneOffset.UTC);

    verify(summaryService, never()).getDailySummary(any(), any());
    verify(notificationService, never()).notifyGoalAchieved(any(), any());
  }

  @Test
  void evaluateGoals_notifiesAndStamps_whenGoalMet() {
    Goal g = Goal.builder().id(UUID.randomUUID()).user(user)
        .metric(GoalMetric.CALORIES_BURNED).targetValue(300).active(true).build();
    when(goalRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(g));

    SummaryResponse summary = SummaryResponse.builder().totalCaloriesBurned(400).build();
    when(summaryService.getDailySummary(userId, ZoneOffset.UTC)).thenReturn(summary);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    goalService.evaluateGoals(userId, ZoneOffset.UTC);

    assertThat(g.getLastAchievedDate()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
    verify(goalRepository).save(g);
    verify(notificationService).notifyGoalAchieved(user, g);
  }

  @Test
  void evaluateGoals_skips_whenAlreadyAchievedToday() {
    Goal g = Goal.builder().id(UUID.randomUUID()).user(user)
        .metric(GoalMetric.CALORIES_BURNED).targetValue(300).active(true)
        .lastAchievedDate(LocalDate.now(ZoneOffset.UTC)).build();
    when(goalRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(g));

    SummaryResponse summary = SummaryResponse.builder().totalCaloriesBurned(400).build();
    when(summaryService.getDailySummary(userId, ZoneOffset.UTC)).thenReturn(summary);

    goalService.evaluateGoals(userId, ZoneOffset.UTC);

    verify(notificationService, never()).notifyGoalAchieved(any(), any());
    verify(goalRepository, never()).save(any());
  }

  @Test
  void evaluateGoals_skips_whenGoalNotMet() {
    Goal g = Goal.builder().id(UUID.randomUUID()).user(user)
        .metric(GoalMetric.STEPS).targetValue(10000).active(true).build();
    when(goalRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(g));

    SummaryResponse summary = SummaryResponse.builder().totalSteps(5000).build();
    when(summaryService.getDailySummary(userId, ZoneOffset.UTC)).thenReturn(summary);

    goalService.evaluateGoals(userId, ZoneOffset.UTC);

    verify(notificationService, never()).notifyGoalAchieved(any(), any());
    verify(goalRepository, never()).save(any());
  }

  @Test
  void evaluateGoals_handlesAllMetrics() {
    SummaryResponse summary = SummaryResponse.builder()
        .totalCaloriesBurned(500)
        .totalDurationMinutes(60)
        .totalActivities(3)
        .totalDistanceKm(10.0)
        .totalSteps(12000)
        .build();

    GoalMetric[] metrics = GoalMetric.values();
    double[] targets = {400, 45, 2, 8.0, 10000};

    for (int i = 0; i < metrics.length; i++) {
      reset(goalRepository, summaryService, userRepository, notificationService);

      Goal g = Goal.builder().id(UUID.randomUUID()).user(user)
          .metric(metrics[i]).targetValue(targets[i]).active(true).build();
      when(goalRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(g));
      when(summaryService.getDailySummary(userId, ZoneOffset.UTC)).thenReturn(summary);
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      goalService.evaluateGoals(userId, ZoneOffset.UTC);

      verify(notificationService).notifyGoalAchieved(user, g);
    }
  }
}
