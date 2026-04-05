package com.healthcare.activitytracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.entity.Activity;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.repository.projection.ActivityTypeSummaryProjection;
import com.healthcare.activitytracker.repository.projection.DistanceStepsProjection;
import com.healthcare.activitytracker.repository.projection.SourceCountProjection;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test") // In case there is a test profile
class ActivityRepositoryTest {

  @Autowired private ActivityRepository activityRepository;

  @Autowired private UserRepository userRepository;

  private User testUser;
  private Activity activity1;
  private Activity activity2;

  @BeforeEach
  void setUp() {
    testUser =
        User.builder()
            .email("test.repo@example.com")
            .passwordHash("hash")
            .fullName("Test Repo User")
            .build();
    userRepository.save(testUser);

    activity1 =
        Activity.builder()
            .user(testUser)
            .activityType(ActivityType.RUNNING)
            .source(ActivitySource.MANUAL)
            .startedAt(LocalDateTime.now().minusDays(2))
            .endedAt(LocalDateTime.now().minusDays(2).plusHours(1))
            .distanceKm(5.0)
            .steps(6000)
            .caloriesBurned(400.0)
            .durationMinutes(60)
            .build();

    activity2 =
        Activity.builder()
            .user(testUser)
            .activityType(ActivityType.CYCLING)
            .source(ActivitySource.IOT)
            .startedAt(LocalDateTime.now().minusDays(1))
            .endedAt(LocalDateTime.now().minusDays(1).plusHours(2))
            .distanceKm(20.0)
            .steps(0)
            .caloriesBurned(800.0)
            .durationMinutes(120)
            .build();

    activityRepository.save(activity1);
    activityRepository.save(activity2);
  }

  @Test
  void testSumDistanceAndSteps() {
    List<DistanceStepsProjection> result =
        activityRepository.sumDistanceAndSteps(
            testUser.getId(), LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(1));

    assertThat(result).isNotEmpty();
    DistanceStepsProjection row = result.get(0);

    // 5.0 + 20.0 = 25.0 distance, 6000 + 0 = 6000 steps
    assertThat(row.getTotalDistance()).isEqualTo(25.0);
    assertThat(row.getTotalSteps()).isEqualTo(6000L);
  }

  @Test
  void testCountBySourceInRange() {
    List<SourceCountProjection> result =
        activityRepository.countBySourceInRange(
            testUser.getId(), LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(1));

    assertThat(result).hasSize(2);
  }

  @Test
  void testSummarizeByActivityTypeInRange() {
    List<ActivityTypeSummaryProjection> result =
        activityRepository.summarizeByActivityTypeInRange(
            testUser.getId(), LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(1));

    assertThat(result).hasSize(2);
  }

  @Test
  void testFindByUserIdAndDateRange() {
    List<Activity> list =
        activityRepository.findByUserIdAndDateRange(
            testUser.getId(), LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(1));

    assertThat(list).hasSize(2);
  }
}
