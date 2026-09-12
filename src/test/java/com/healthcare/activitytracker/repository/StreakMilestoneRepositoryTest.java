package com.healthcare.activitytracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.entity.StreakMilestone;
import com.healthcare.activitytracker.model.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class StreakMilestoneRepositoryTest {

  @Autowired private StreakMilestoneRepository milestoneRepository;
  @Autowired private UserRepository userRepository;

  private User testUser;
  private User otherUser;

  @BeforeEach
  void setUp() {
    testUser =
        userRepository.save(
            User.builder()
                .email("milestones.repo@example.com")
                .passwordHash("hash")
                .fullName("Milestone User")
                .build());
    otherUser =
        userRepository.save(
            User.builder()
                .email("milestones.other@example.com")
                .passwordHash("hash")
                .fullName("Other User")
                .build());

    milestoneRepository.save(
        StreakMilestone.builder()
            .user(testUser)
            .milestoneDays(3)
            .achievedAt(LocalDateTime.now().minusDays(10))
            .triggeringActivityId(UUID.randomUUID())
            .build());
    milestoneRepository.save(
        StreakMilestone.builder()
            .user(testUser)
            .milestoneDays(7)
            .achievedAt(LocalDateTime.now().minusDays(5))
            .triggeringActivityId(UUID.randomUUID())
            .build());
    milestoneRepository.save(
        StreakMilestone.builder()
            .user(otherUser)
            .milestoneDays(30)
            .achievedAt(LocalDateTime.now().minusDays(1))
            .triggeringActivityId(UUID.randomUUID())
            .build());
  }

  @Test
  void findByUserIdOrderByMilestoneDaysDesc_returnsOnlyThatUsersRows_longestFirst() {
    List<StreakMilestone> result =
        milestoneRepository.findByUserIdOrderByMilestoneDaysDesc(testUser.getId());

    assertThat(result).extracting(StreakMilestone::getMilestoneDays).containsExactly(7, 3);
  }

  @Test
  void findByUserIdOrderByMilestoneDaysDesc_returnsEmptyList_forAUserWithNoMilestones() {
    User freshUser =
        userRepository.save(
            User.builder()
                .email("milestones.fresh@example.com")
                .passwordHash("hash")
                .fullName("Fresh User")
                .build());

    assertThat(milestoneRepository.findByUserIdOrderByMilestoneDaysDesc(freshUser.getId()))
        .isEmpty();
  }
}
