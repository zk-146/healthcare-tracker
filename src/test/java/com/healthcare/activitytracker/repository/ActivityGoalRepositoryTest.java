package com.healthcare.activitytracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.entity.ActivityGoal;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.model.enums.GoalPeriod;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Query-behaviour tests on H2.
 *
 * <p>Uniqueness is <em>not</em> covered here and cannot be: the active-goal constraint is a partial
 * unique index declared in {@code V6__activity_goals.sql}, the H2 profile disables Flyway and
 * builds its schema from the entities instead, and a partial index has no JPA annotation to be
 * built from. {@link ActivityGoalRepositoryPostgresTest} is where that rule is exercised.
 */
@DataJpaTest
@ActiveProfiles("test")
class ActivityGoalRepositoryTest {

  @Autowired private ActivityGoalRepository goalRepository;
  @Autowired private UserRepository userRepository;

  private User testUser;
  private User otherUser;
  private ActivityGoal activeMinutes;
  private ActivityGoal activeSteps;
  private ActivityGoal archivedDistance;

  private User newUser(String email) {
    return userRepository.save(
        User.builder().email(email).passwordHash("hash").fullName("Goal User").build());
  }

  private ActivityGoal save(
      User user, GoalMetric metric, GoalPeriod period, double target, boolean active) {
    return goalRepository.save(
        ActivityGoal.builder()
            .user(user)
            .metric(metric)
            .period(period)
            .targetValue(target)
            .active(active)
            .build());
  }

  @BeforeEach
  void setUp() {
    testUser = newUser("goals.repo@example.com");
    otherUser = newUser("goals.other@example.com");

    activeMinutes = save(testUser, GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150, true);
    activeSteps = save(testUser, GoalMetric.STEPS, GoalPeriod.DAILY, 8_000, true);
    archivedDistance = save(testUser, GoalMetric.DISTANCE_KM, GoalPeriod.WEEKLY, 20, false);
    save(otherUser, GoalMetric.SESSIONS, GoalPeriod.MONTHLY, 12, true);
  }

  @Test
  void findActive_returnsOnlyThatUsersLiveGoals() {
    List<ActivityGoal> result =
        goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(testUser.getId());

    assertThat(result)
        .extracting(ActivityGoal::getId)
        .containsExactlyInAnyOrder(activeMinutes.getId(), activeSteps.getId())
        .doesNotContain(archivedDistance.getId());
  }

  @Test
  void findActive_ordersByCreationAscending() {
    // Asserted as a sorted sequence rather than a fixed id order: @CreationTimestamp is set by
    // Hibernate on persist, so three inserts in the same millisecond could legitimately tie and
    // a hardcoded order would be flaky. Sortedness holds either way.
    List<LocalDateTime> createdAt =
        goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(testUser.getId()).stream()
            .map(ActivityGoal::getCreatedAt)
            .toList();

    assertThat(createdAt).isSorted();
  }

  @Test
  void findActive_returnsEmpty_forAUserWithNoGoals() {
    assertThat(
            goalRepository.findByUserIdAndActiveTrueOrderByCreatedAtAsc(
                newUser("goals.fresh@example.com").getId()))
        .isEmpty();
  }

  @Test
  void findByIdAndUser_findsTheCallersLiveGoal() {
    Optional<ActivityGoal> found =
        goalRepository.findByIdAndUserIdAndActiveTrue(activeMinutes.getId(), testUser.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getTargetValue()).isEqualTo(150.0);
  }

  @Test
  void findByIdAndUser_doesNotLeakAnotherUsersGoal() {
    assertThat(
            goalRepository.findByIdAndUserIdAndActiveTrue(activeMinutes.getId(), otherUser.getId()))
        .isEmpty();
  }

  @Test
  void findByIdAndUser_doesNotReturnAnArchivedGoal() {
    assertThat(
            goalRepository.findByIdAndUserIdAndActiveTrue(
                archivedDistance.getId(), testUser.getId()))
        .isEmpty();
  }

  @Test
  void exists_isTrueForALiveMetricPeriodPair() {
    assertThat(
            goalRepository.existsByUserIdAndMetricAndPeriodAndActiveTrue(
                testUser.getId(), GoalMetric.MINUTES, GoalPeriod.WEEKLY))
        .isTrue();
  }

  @Test
  void exists_isFalseWhenOnlyAnArchivedGoalCoversThatPair() {
    // This is what lets a user set a new weekly-distance goal after retiring the old one.
    assertThat(
            goalRepository.existsByUserIdAndMetricAndPeriodAndActiveTrue(
                testUser.getId(), GoalMetric.DISTANCE_KM, GoalPeriod.WEEKLY))
        .isFalse();
  }

  @Test
  void exists_isFalseForTheSamePairUnderADifferentPeriod() {
    assertThat(
            goalRepository.existsByUserIdAndMetricAndPeriodAndActiveTrue(
                testUser.getId(), GoalMetric.MINUTES, GoalPeriod.MONTHLY))
        .isFalse();
  }

  @Test
  void count_tallsOnlyLiveGoals() {
    assertThat(goalRepository.countByUserIdAndActiveTrue(testUser.getId())).isEqualTo(2);
    assertThat(goalRepository.countByUserIdAndActiveTrue(otherUser.getId())).isEqualTo(1);
  }

  @Test
  void deleteAllByUserId_removesArchivedRowsTooAndSparesOtherUsers() {
    int removed = goalRepository.deleteAllByUserId(testUser.getId());

    assertThat(removed).isEqualTo(3);
    assertThat(goalRepository.countByUserIdAndActiveTrue(testUser.getId())).isZero();
    assertThat(goalRepository.countByUserIdAndActiveTrue(otherUser.getId())).isEqualTo(1);
  }
}
