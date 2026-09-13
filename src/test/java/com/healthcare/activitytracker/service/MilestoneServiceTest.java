package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.model.dto.MilestoneProgressResponse;
import com.healthcare.activitytracker.model.dto.MilestoneResponse;
import com.healthcare.activitytracker.model.entity.StreakMilestone;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

  @Mock private StreakMilestoneRepository milestoneRepository;
  @Mock private SummaryService summaryService;

  private final UUID userId = UUID.randomUUID();

  private MilestoneService service() {
    return new MilestoneService(milestoneRepository, summaryService);
  }

  private StreakMilestone milestone(int days, LocalDateTime achievedAt) {
    return StreakMilestone.builder()
        .id(UUID.randomUUID())
        .milestoneDays(days)
        .achievedAt(achievedAt)
        .triggeringActivityId(UUID.randomUUID())
        .build();
  }

  @Test
  void getMilestones_mapsRepositoryRowsInReturnedOrder() {
    LocalDateTime sevenDayAt = LocalDateTime.of(2026, 8, 20, 9, 0);
    LocalDateTime threeDayAt = LocalDateTime.of(2026, 8, 15, 9, 0);
    when(milestoneRepository.findByUserIdOrderByMilestoneDaysDesc(userId))
        .thenReturn(List.of(milestone(7, sevenDayAt), milestone(3, threeDayAt)));

    List<MilestoneResponse> result = service().getMilestones(userId);

    assertThat(result)
        .extracting(MilestoneResponse::getMilestoneDays, MilestoneResponse::getAchievedAt)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(7, sevenDayAt),
            org.assertj.core.groups.Tuple.tuple(3, threeDayAt));
  }

  @Test
  void getMilestones_returnsEmptyListForAUserWithNoMilestonesYet() {
    when(milestoneRepository.findByUserIdOrderByMilestoneDaysDesc(userId)).thenReturn(List.of());

    assertThat(service().getMilestones(userId)).isEmpty();
  }

  @Test
  void getProgress_reportsNextThresholdAndDaysRemaining() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(12);

    MilestoneProgressResponse result = service().getProgress(userId, ZoneOffset.UTC);

    assertThat(result.getCurrentStreak()).isEqualTo(12);
    assertThat(result.getNextThreshold()).isEqualTo(14);
    assertThat(result.getDaysRemaining()).isEqualTo(2);
    assertThat(result.getProgress()).isEqualTo(12 / 14.0);
    assertThat(result.getLadder()).containsExactly(3, 7, 14, 30, 60, 100, 365);
  }

  @Test
  void getProgress_startsAtTheFirstRungForAUserWithNoStreak() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(0);

    MilestoneProgressResponse result = service().getProgress(userId, ZoneOffset.UTC);

    assertThat(result.getCurrentStreak()).isZero();
    assertThat(result.getNextThreshold()).isEqualTo(3);
    assertThat(result.getDaysRemaining()).isEqualTo(3);
    assertThat(result.getProgress()).isZero();
  }

  @Test
  void getProgress_advancesPastAThresholdTheStreakHasExactlyReached() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(3);

    MilestoneProgressResponse result = service().getProgress(userId, ZoneOffset.UTC);

    assertThat(result.getNextThreshold()).isEqualTo(7);
    assertThat(result.getDaysRemaining()).isEqualTo(4);
  }

  @Test
  void getProgress_nullsTheNextRungAndReportsFullProgressOnceTheLadderIsExhausted() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(400);

    MilestoneProgressResponse result = service().getProgress(userId, ZoneOffset.UTC);

    assertThat(result.getCurrentStreak()).isEqualTo(400);
    assertThat(result.getNextThreshold()).isNull();
    assertThat(result.getDaysRemaining()).isNull();
    assertThat(result.getProgress()).isEqualTo(1.0);
    assertThat(result.getLadder()).containsExactly(3, 7, 14, 30, 60, 100, 365);
  }

  @Test
  void getProgress_passesTheCallersZoneThroughToTheStreakCalculation() {
    ZoneId newYork = ZoneId.of("America/New_York");
    when(summaryService.getCurrentStreak(userId, newYork)).thenReturn(5);

    assertThat(service().getProgress(userId, newYork).getCurrentStreak()).isEqualTo(5);
  }
}
