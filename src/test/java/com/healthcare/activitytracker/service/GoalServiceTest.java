package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

  @Mock private ActivityGoalRepository goalRepository;
  @Mock private UserRepository userRepository;
  @Mock private SummaryService summaryService;

  private GoalService goalService;

  private final UUID userId = UUID.randomUUID();
  private final UUID goalId = UUID.randomUUID();
  private final ZoneId zone = ZoneOffset.UTC;

  /** Monday of the reference week — the day a WEEKLY period starts. */
  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

  @BeforeEach
  void setUp() {
    goalService = new GoalService(goalRepository, userRepository, summaryService);
  }

  private ActivityGoal goal(GoalMetric metric, GoalPeriod period, double target) {
    return ActivityGoal.builder()
        .id(goalId)
        .user(User.builder().id(userId).build())
        .metric(metric)
        .period(period)
        .targetValue(target)
        .active(true)
        .build();
  }

  /**
   * A summary whose window is stated outright rather than derived from the clock, so the pace
   * assertions below are about the arithmetic and not about what day the suite happens to run.
   */
  private SummaryResponse summary(LocalDate from, LocalDate to) {
    return SummaryResponse.builder().from(from).to(to).build();
  }

  private SummaryResponse weeklySummaryWithMinutes(LocalDate to, long minutes) {
    return SummaryResponse.builder().from(MONDAY).to(to).totalDurationMinutes(minutes).build();
  }

  // ---------------------------------------------------------------- getGoals

  @Test
  void getGoals_returnsEmptyList_whenUserHasNone() {
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId)).thenReturn(List.of());

    assertThat(goalService.getGoals(userId, zone)).isEmpty();
    verify(summaryService, never()).getWeeklySummary(any(), any());
  }

  @Test
  void getGoals_mapsMetricPeriodAndUnit() {
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(goal(GoalMetric.DISTANCE_KM, GoalPeriod.WEEKLY, 20)));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(
            SummaryResponse.builder()
                .from(MONDAY)
                .to(MONDAY.plusDays(2))
                .totalDistanceKm(8.0)
                .build());

    GoalResponse response = goalService.getGoals(userId, zone).get(0);

    assertThat(response.getMetric()).isEqualTo("DISTANCE_KM");
    assertThat(response.getPeriod()).isEqualTo("WEEKLY");
    assertThat(response.getUnit()).isEqualTo("km");
    assertThat(response.getTargetValue()).isEqualTo(20.0);
    assertThat(response.getProgress().getCurrentValue()).isEqualTo(8.0);
  }

  @Test
  void getGoals_computesOneSummaryPerPeriod_notPerGoal() {
    // Two weekly goals plus one monthly: the weekly summary must be fetched once, not twice.
    // getSummary recomputes the streak on every call, so per-goal fetching would multiply a
    // scan its own javadoc already flags as expensive.
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(
            List.of(
                goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150),
                goal(GoalMetric.STEPS, GoalPeriod.WEEKLY, 70_000),
                goal(GoalMetric.SESSIONS, GoalPeriod.MONTHLY, 12)));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(summary(MONDAY, MONDAY.plusDays(2)));
    when(summaryService.getMonthlySummary(userId, zone))
        .thenReturn(summary(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 9)));

    assertThat(goalService.getGoals(userId, zone)).hasSize(3);

    verify(summaryService, times(1)).getWeeklySummary(userId, zone);
    verify(summaryService, times(1)).getMonthlySummary(userId, zone);
    verify(summaryService, never()).getDailySummary(any(), any());
  }

  // ------------------------------------------------------------- pace / progress

  @Test
  void progress_isBehindPace_whenShortOfTheEvenRun() {
    // 150 min/week, Wednesday (3 of 7 days gone), 60 min logged.
    // An even pace would want 150 * 3/7 = 64.3 by now.
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150)));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(weeklySummaryWithMinutes(MONDAY.plusDays(2), 60));

    GoalResponse.Progress progress = goalService.getGoals(userId, zone).get(0).getProgress();

    assertThat(progress.getCurrentValue()).isEqualTo(60.0);
    assertThat(progress.getPercentComplete()).isEqualTo(40.0);
    assertThat(progress.getDaysElapsed()).isEqualTo(3);
    assertThat(progress.getDaysTotal()).isEqualTo(7);
    assertThat(progress.getDaysRemaining()).isEqualTo(4);
    assertThat(progress.getExpectedByNow()).isEqualTo(64.3);
    assertThat(progress.isOnTrack()).isFalse();
    assertThat(progress.isAchieved()).isFalse();
  }

  @Test
  void progress_isOnPace_whenAheadOfTheEvenRun() {
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150)));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(weeklySummaryWithMinutes(MONDAY.plusDays(2), 70));

    GoalResponse.Progress progress = goalService.getGoals(userId, zone).get(0).getProgress();

    assertThat(progress.isOnTrack()).isTrue();
    assertThat(progress.isAchieved()).isFalse();
  }

  @Test
  void progress_isAchievedAndOnTrack_whenTargetExactlyMet() {
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150)));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(weeklySummaryWithMinutes(MONDAY.plusDays(1), 150));

    GoalResponse.Progress progress = goalService.getGoals(userId, zone).get(0).getProgress();

    assertThat(progress.isAchieved()).isTrue();
    assertThat(progress.getPercentComplete()).isEqualTo(100.0);
    assertThat(progress.isOnTrack()).isTrue();
  }

  @Test
  void progress_reportsPercentAboveOneHundred_whenGoalExceeded() {
    // Uncapped on purpose: beating a goal by half should read as 150%, not a flat 100%.
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150)));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(weeklySummaryWithMinutes(MONDAY.plusDays(6), 225));

    GoalResponse.Progress progress = goalService.getGoals(userId, zone).get(0).getProgress();

    assertThat(progress.getPercentComplete()).isEqualTo(150.0);
    assertThat(progress.isAchieved()).isTrue();
  }

  @Test
  void progress_isOnTrack_whenAchievedOnTheFinalDay() {
    // On day 7 an even pace demands the whole target, so "achieved" is what rescues onTrack here.
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150)));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(weeklySummaryWithMinutes(MONDAY.plusDays(6), 150));

    GoalResponse.Progress progress = goalService.getGoals(userId, zone).get(0).getProgress();

    assertThat(progress.getDaysElapsed()).isEqualTo(7);
    assertThat(progress.getDaysRemaining()).isZero();
    assertThat(progress.getExpectedByNow()).isEqualTo(150.0);
    assertThat(progress.isOnTrack()).isTrue();
  }

  @Test
  void progress_scoresDailyGoalOverASingleDay() {
    LocalDate today = LocalDate.of(2026, 9, 9);
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(goal(GoalMetric.STEPS, GoalPeriod.DAILY, 8_000)));
    when(summaryService.getDailySummary(userId, zone))
        .thenReturn(SummaryResponse.builder().from(today).to(today).totalSteps(6_000).build());

    GoalResponse.Progress progress = goalService.getGoals(userId, zone).get(0).getProgress();

    assertThat(progress.getDaysTotal()).isEqualTo(1);
    assertThat(progress.getDaysElapsed()).isEqualTo(1);
    assertThat(progress.getDaysRemaining()).isZero();
    assertThat(progress.getExpectedByNow()).isEqualTo(8_000.0);
    assertThat(progress.isOnTrack()).isFalse();
  }

  @Test
  void progress_usesTheActualMonthLength_forMonthlyGoals() {
    // February, so the denominator must be 28 rather than a hardcoded 30 or 31.
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(goal(GoalMetric.SESSIONS, GoalPeriod.MONTHLY, 28)));
    when(summaryService.getMonthlySummary(userId, zone))
        .thenReturn(
            SummaryResponse.builder()
                .from(LocalDate.of(2026, 2, 1))
                .to(LocalDate.of(2026, 2, 14))
                .totalActivities(14)
                .build());

    GoalResponse.Progress progress = goalService.getGoals(userId, zone).get(0).getProgress();

    assertThat(progress.getDaysTotal()).isEqualTo(28);
    assertThat(progress.getDaysElapsed()).isEqualTo(14);
    assertThat(progress.getExpectedByNow()).isEqualTo(14.0);
    assertThat(progress.isOnTrack()).isTrue();
  }

  @Test
  void progress_readsCaloriesMetricFromTheSummary() {
    when(goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(goal(GoalMetric.CALORIES, GoalPeriod.WEEKLY, 3_500)));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(
            SummaryResponse.builder()
                .from(MONDAY)
                .to(MONDAY.plusDays(3))
                .totalCaloriesBurned(1_750.5)
                .build());

    GoalResponse.Progress progress = goalService.getGoals(userId, zone).get(0).getProgress();

    assertThat(progress.getCurrentValue()).isEqualTo(1_750.5);
  }

  // -------------------------------------------------------------- createGoal

  private GoalRequest request(GoalMetric metric, GoalPeriod period, double target) {
    GoalRequest request = new GoalRequest();
    request.setMetric(metric);
    request.setPeriod(period);
    request.setTargetValue(target);
    return request;
  }

  @Test
  void createGoal_savesActiveGoalAndReturnsProgress() {
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(User.builder().id(userId).build()));
    when(goalRepository.existsByUserIdAndMetricAndPeriodAndActiveTrue(
            userId, GoalMetric.MINUTES, GoalPeriod.WEEKLY))
        .thenReturn(false);
    when(goalRepository.countByUserIdAndActiveTrue(userId)).thenReturn(0L);
    when(goalRepository.save(any(ActivityGoal.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, ActivityGoal.class));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(weeklySummaryWithMinutes(MONDAY.plusDays(1), 30));

    GoalResponse created =
        goalService.createGoal(userId, request(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150), zone);

    assertThat(created.getTargetValue()).isEqualTo(150.0);
    assertThat(created.getProgress().getCurrentValue()).isEqualTo(30.0);

    verify(goalRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                saved ->
                    saved.isActive()
                        && saved.getMetric() == GoalMetric.MINUTES
                        && saved.getPeriod() == GoalPeriod.WEEKLY));
  }

  @Test
  void createGoal_conflicts_whenALiveGoalAlreadyCoversThatMetricAndPeriod() {
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(User.builder().id(userId).build()));
    when(goalRepository.existsByUserIdAndMetricAndPeriodAndActiveTrue(
            userId, GoalMetric.MINUTES, GoalPeriod.WEEKLY))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                goalService.createGoal(
                    userId, request(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150), zone))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("already exists");

    verify(goalRepository, never()).save(any());
  }

  @Test
  void createGoal_conflicts_atTheActiveGoalCeiling() {
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(User.builder().id(userId).build()));
    when(goalRepository.existsByUserIdAndMetricAndPeriodAndActiveTrue(any(), any(), any()))
        .thenReturn(false);
    when(goalRepository.countByUserIdAndActiveTrue(userId))
        .thenReturn((long) GoalService.MAX_ACTIVE_GOALS);

    assertThatThrownBy(
            () ->
                goalService.createGoal(
                    userId, request(GoalMetric.STEPS, GoalPeriod.DAILY, 8_000), zone))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining(String.valueOf(GoalService.MAX_ACTIVE_GOALS));

    verify(goalRepository, never()).save(any());
  }

  @Test
  void createGoal_throwsNotFound_whenTheUserIsGone() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                goalService.createGoal(
                    userId, request(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150), zone))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(goalRepository, never()).save(any());
  }

  // -------------------------------------------------------------- updateGoal

  private GoalUpdateRequest updateTo(double target) {
    GoalUpdateRequest request = new GoalUpdateRequest();
    request.setTargetValue(target);
    return request;
  }

  @Test
  void updateGoal_changesOnlyTheTarget() {
    ActivityGoal existing = goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150);
    when(goalRepository.findByIdAndUserIdAndActiveTrue(goalId, userId))
        .thenReturn(Optional.of(existing));
    when(goalRepository.save(any(ActivityGoal.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, ActivityGoal.class));
    when(summaryService.getWeeklySummary(userId, zone))
        .thenReturn(weeklySummaryWithMinutes(MONDAY.plusDays(1), 30));

    GoalResponse updated = goalService.updateGoal(userId, goalId, updateTo(200), zone);

    assertThat(updated.getTargetValue()).isEqualTo(200.0);
    assertThat(updated.getMetric()).isEqualTo("MINUTES");
    assertThat(updated.getPeriod()).isEqualTo("WEEKLY");
    assertThat(existing.getTargetValue()).isEqualTo(200.0);
  }

  @Test
  void updateGoal_throwsNotFound_whenTheGoalIsAnotherUsersOrRetired() {
    // The repository query is scoped by user and active, so both cases arrive here as empty.
    when(goalRepository.findByIdAndUserIdAndActiveTrue(goalId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> goalService.updateGoal(userId, goalId, updateTo(200), zone))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Goal not found");

    verify(goalRepository, never()).save(any());
  }

  @Test
  void updateGoal_rejectsATargetAboveTheStoredMetricsCeiling() {
    // 1440 minutes/day * 7 = 10080 is the weekly ceiling; this asks for more. The bound depends
    // on the stored metric, which is exactly why Bean Validation cannot enforce it here.
    when(goalRepository.findByIdAndUserIdAndActiveTrue(goalId, userId))
        .thenReturn(Optional.of(goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150)));

    assertThatThrownBy(() -> goalService.updateGoal(userId, goalId, updateTo(10_081), zone))
        .isInstanceOf(FieldValidationException.class)
        .hasMessageContaining("10080");

    verify(goalRepository, never()).save(any());
  }

  @Test
  void updateGoal_rejectsANonPositiveTarget() {
    when(goalRepository.findByIdAndUserIdAndActiveTrue(goalId, userId))
        .thenReturn(Optional.of(goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150)));

    assertThatThrownBy(() -> goalService.updateGoal(userId, goalId, updateTo(0), zone))
        .isInstanceOf(FieldValidationException.class)
        .hasMessageContaining("greater than 0");
  }

  @Test
  void updateGoal_namesTheTargetValueField_soTheErrorLandsOnTheRightInput() {
    when(goalRepository.findByIdAndUserIdAndActiveTrue(goalId, userId))
        .thenReturn(Optional.of(goal(GoalMetric.STEPS, GoalPeriod.DAILY, 8_000)));

    assertThatThrownBy(() -> goalService.updateGoal(userId, goalId, updateTo(-5), zone))
        .isInstanceOf(FieldValidationException.class)
        .extracting(cause -> ((FieldValidationException) cause).getField())
        .isEqualTo("targetValue");
  }

  // -------------------------------------------------------------- deleteGoal

  @Test
  void deleteGoal_archivesRatherThanDeletes() {
    ActivityGoal existing = goal(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150);
    when(goalRepository.findByIdAndUserIdAndActiveTrue(goalId, userId))
        .thenReturn(Optional.of(existing));

    goalService.deleteGoal(userId, goalId);

    assertThat(existing.isActive()).isFalse();
    verify(goalRepository).save(existing);
    verify(goalRepository, never()).delete(any());
    verify(goalRepository, never()).deleteById(eq(goalId));
  }

  @Test
  void deleteGoal_throwsNotFound_whenAlreadyRetired() {
    when(goalRepository.findByIdAndUserIdAndActiveTrue(goalId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> goalService.deleteGoal(userId, goalId))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(goalRepository, never()).save(any());
  }
}
