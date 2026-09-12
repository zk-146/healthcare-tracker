package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.model.dto.MilestoneResponse;
import com.healthcare.activitytracker.model.entity.StreakMilestone;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

  @Mock private StreakMilestoneRepository milestoneRepository;

  private final UUID userId = UUID.randomUUID();

  private MilestoneService service() {
    return new MilestoneService(milestoneRepository);
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
}
