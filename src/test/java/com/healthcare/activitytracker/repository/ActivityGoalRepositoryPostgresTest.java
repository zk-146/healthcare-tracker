package com.healthcare.activitytracker.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.activitytracker.model.entity.ActivityGoal;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.model.enums.GoalPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Goal repository tests against a real PostgreSQL instance (Testcontainers).
 *
 * <p>The one-active-goal-per-metric-and-period rule is a <em>partial</em> unique index ({@code ...
 * WHERE active}) declared in {@code V6__activity_goals.sql}. H2 builds its schema from the entities
 * with Flyway disabled, and a partial index cannot be expressed as a JPA annotation, so this is the
 * only place the rule exists to be tested. It also exercises V6 for real and, via {@code ddl-auto:
 * validate} inherited from the default profile, proves the migration matches {@link ActivityGoal}.
 *
 * <p>Each uniqueness assertion is the final act of its test: a constraint violation poisons the
 * surrounding transaction, so nothing can be asserted after it.
 *
 * <p>Skipped automatically when Docker is unavailable; runs in CI.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ActivityGoalRepositoryPostgresTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private ActivityGoalRepository goalRepository;
  @Autowired private UserRepository userRepository;

  private User testUser;

  @BeforeEach
  void setUp() {
    goalRepository.deleteAll();
    testUser = newUser("goals.pg@example.com");
  }

  private User newUser(String email) {
    return userRepository.saveAndFlush(
        User.builder().email(email).passwordHash("hash").fullName("Goal User").build());
  }

  private ActivityGoal goal(User user, GoalMetric metric, GoalPeriod period, boolean active) {
    return ActivityGoal.builder()
        .user(user)
        .metric(metric)
        .period(period)
        .targetValue(150.0)
        .active(active)
        .build();
  }

  @Test
  void aSecondLiveGoalForTheSameMetricAndPeriodIsRejected() {
    goalRepository.saveAndFlush(goal(testUser, GoalMetric.MINUTES, GoalPeriod.WEEKLY, true));

    assertThatThrownBy(
            () ->
                goalRepository.saveAndFlush(
                    goal(testUser, GoalMetric.MINUTES, GoalPeriod.WEEKLY, true)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anArchivedGoalDoesNotBlockANewOneForTheSameMetricAndPeriod() {
    // The whole reason the index is partial: retiring a goal must free its slot.
    goalRepository.saveAndFlush(goal(testUser, GoalMetric.MINUTES, GoalPeriod.WEEKLY, false));

    ActivityGoal replacement =
        goalRepository.saveAndFlush(goal(testUser, GoalMetric.MINUTES, GoalPeriod.WEEKLY, true));

    assertThat(replacement.getId()).isNotNull();
    assertThat(goalRepository.countByUserIdAndActiveTrue(testUser.getId())).isEqualTo(1);
  }

  @Test
  void severalArchivedGoalsMayCoexistForTheSameMetricAndPeriod() {
    // Archived rows are entirely unconstrained, so a user who has revised a target repeatedly
    // accumulates history rather than hitting a constraint.
    goalRepository.saveAndFlush(goal(testUser, GoalMetric.STEPS, GoalPeriod.DAILY, false));
    goalRepository.saveAndFlush(goal(testUser, GoalMetric.STEPS, GoalPeriod.DAILY, false));

    assertThat(goalRepository.findAll()).hasSize(2);
    assertThat(goalRepository.countByUserIdAndActiveTrue(testUser.getId())).isZero();
  }

  @Test
  void theSameMetricAndPeriodMayBeLiveForTwoDifferentUsers() {
    goalRepository.saveAndFlush(goal(testUser, GoalMetric.MINUTES, GoalPeriod.WEEKLY, true));
    User second = newUser("goals.pg.second@example.com");

    goalRepository.saveAndFlush(goal(second, GoalMetric.MINUTES, GoalPeriod.WEEKLY, true));

    assertThat(goalRepository.countByUserIdAndActiveTrue(testUser.getId())).isEqualTo(1);
    assertThat(goalRepository.countByUserIdAndActiveTrue(second.getId())).isEqualTo(1);
  }

  @Test
  void differentPeriodsForTheSameMetricMayBothBeLive() {
    goalRepository.saveAndFlush(goal(testUser, GoalMetric.MINUTES, GoalPeriod.WEEKLY, true));
    goalRepository.saveAndFlush(goal(testUser, GoalMetric.MINUTES, GoalPeriod.MONTHLY, true));

    assertThat(goalRepository.countByUserIdAndActiveTrue(testUser.getId())).isEqualTo(2);
  }
}
