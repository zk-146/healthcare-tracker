package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.model.dto.SummaryResponse;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.repository.ActivityRepository;
import com.healthcare.activitytracker.repository.projection.ActivityTypeSummaryProjection;
import com.healthcare.activitytracker.repository.projection.DistanceStepsProjection;
import com.healthcare.activitytracker.repository.projection.SourceCountProjection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

  @Mock private ActivityRepository activityRepository;

  private SummaryService summaryService;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() throws Exception {
    summaryService = new SummaryService(activityRepository);
    // Inject maxRangeDays (normally set by @Value) so range-check doesn't reject test dates
    java.lang.reflect.Field f = SummaryService.class.getDeclaredField("maxRangeDays");
    f.setAccessible(true);
    f.set(summaryService, 365);
  }

  @Test
  void getSummary_aggregatesFromDbQueries() {
    SourceCountProjection sourceRow = mock(SourceCountProjection.class);
    when(sourceRow.getSource()).thenReturn(ActivitySource.MANUAL);
    when(sourceRow.getCount()).thenReturn(3L);

    ActivityTypeSummaryProjection typeRow = mock(ActivityTypeSummaryProjection.class);
    when(typeRow.getActivityType()).thenReturn(ActivityType.RUNNING);
    when(typeRow.getSource()).thenReturn(ActivitySource.MANUAL);
    when(typeRow.getCount()).thenReturn(3L);
    when(typeRow.getTotalMinutes()).thenReturn(90L);
    when(typeRow.getTotalCalories()).thenReturn(450.0);

    DistanceStepsProjection distSteps = mock(DistanceStepsProjection.class);
    when(distSteps.getTotalDistance()).thenReturn(15.5);
    when(distSteps.getTotalSteps()).thenReturn(12000L);

    when(activityRepository.countBySourceInRange(eq(userId), any(), any()))
        .thenReturn(List.of(sourceRow));
    when(activityRepository.summarizeByActivityTypeInRange(eq(userId), any(), any()))
        .thenReturn(List.of(typeRow));
    when(activityRepository.sumDistanceAndSteps(eq(userId), any(), any()))
        .thenReturn(List.of(distSteps));
    when(activityRepository.findActiveStartTimesSince(eq(userId), any()))
        .thenReturn(
            List.of(
                LocalDate.now(ZoneId.of("UTC")).atStartOfDay().plusHours(12),
                LocalDate.now(ZoneId.of("UTC")).minusDays(1).atStartOfDay().plusHours(12)));

    SummaryResponse summary =
        summaryService.getSummary(
            userId, LocalDate.now().minusDays(6), LocalDate.now(), ZoneId.of("UTC"));

    assertThat(summary.getTotalActivities()).isEqualTo(3);
    assertThat(summary.getTotalDurationMinutes()).isEqualTo(90);
    assertThat(summary.getTotalCaloriesBurned()).isEqualTo(450.0);
    assertThat(summary.getTotalDistanceKm()).isEqualTo(15.5);
    assertThat(summary.getTotalSteps()).isEqualTo(12000);
    assertThat(summary.getAverageDailyCalories()).isGreaterThan(0);
    assertThat(summary.getStreakDays()).isGreaterThanOrEqualTo(2);
    assertThat(summary.getBySource()).containsKey("MANUAL");
    assertThat(summary.getByActivityType()).hasSize(1);
    assertThat(summary.getByActivityType().get(0).getType()).isEqualTo("RUNNING");
    assertThat(summary.getByActivityType().get(0).getSource()).isEqualTo("MANUAL");
  }

  @Test
  void getSummary_streakDaysIsZeroWhenNoActivities() {
    DistanceStepsProjection distSteps = mock(DistanceStepsProjection.class);
    when(distSteps.getTotalDistance()).thenReturn(0.0);
    when(distSteps.getTotalSteps()).thenReturn(0L);

    when(activityRepository.countBySourceInRange(any(), any(), any())).thenReturn(List.of());
    when(activityRepository.summarizeByActivityTypeInRange(any(), any(), any()))
        .thenReturn(List.of());
    when(activityRepository.sumDistanceAndSteps(any(), any(), any()))
        .thenReturn(List.of(distSteps));
    when(activityRepository.findActiveStartTimesSince(eq(userId), any())).thenReturn(List.of());

    SummaryResponse summary =
        summaryService.getSummary(userId, LocalDate.now(), LocalDate.now(), ZoneId.of("UTC"));
    assertThat(summary.getStreakDays()).isEqualTo(0);
  }

  @Test
  void getSummary_averageDailyCaloriesComputedOverDateRange() {
    ActivityTypeSummaryProjection typeRow = mock(ActivityTypeSummaryProjection.class);
    when(typeRow.getActivityType()).thenReturn(ActivityType.WALKING);
    when(typeRow.getSource()).thenReturn(ActivitySource.MANUAL);
    when(typeRow.getCount()).thenReturn(7L);
    when(typeRow.getTotalMinutes()).thenReturn(210L);
    when(typeRow.getTotalCalories()).thenReturn(700.0);

    DistanceStepsProjection distSteps = mock(DistanceStepsProjection.class);
    when(distSteps.getTotalDistance()).thenReturn(0.0);
    when(distSteps.getTotalSteps()).thenReturn(0L);

    when(activityRepository.countBySourceInRange(any(), any(), any())).thenReturn(List.of());
    when(activityRepository.summarizeByActivityTypeInRange(any(), any(), any()))
        .thenReturn(List.of(typeRow));
    when(activityRepository.sumDistanceAndSteps(any(), any(), any()))
        .thenReturn(List.of(distSteps));
    when(activityRepository.findActiveStartTimesSince(eq(userId), any())).thenReturn(List.of());

    LocalDate from = LocalDate.now().minusDays(6);
    LocalDate to = LocalDate.now();
    SummaryResponse summary = summaryService.getSummary(userId, from, to, ZoneId.of("UTC"));

    // 700 calories / 7 days = 100 per day
    assertThat(summary.getAverageDailyCalories()).isEqualTo(100.0);
  }
}
