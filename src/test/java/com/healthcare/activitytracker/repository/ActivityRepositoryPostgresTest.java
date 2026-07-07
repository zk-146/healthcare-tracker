package com.healthcare.activitytracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.entity.Activity;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository tests against a real PostgreSQL instance (Testcontainers).
 *
 * <p>The regular test suite runs on H2, which silently accepts constructs that can fail on
 * PostgreSQL — notably null-parameter binding in {@code (:param IS NULL OR ...)} JPQL and {@code
 * CAST(... AS java.time.LocalDate)}. This test also exercises the Flyway migrations for real (the
 * H2 profile disables them) and, via {@code ddl-auto: validate} from the default profile, verifies
 * that the migrated schema matches the JPA entities.
 *
 * <p>Skipped automatically when Docker is unavailable; runs in CI.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ActivityRepositoryPostgresTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private ActivityRepository activityRepository;
  @Autowired private UserRepository userRepository;

  private User testUser;

  @BeforeEach
  void setUp() {
    activityRepository.deleteAll();
    userRepository.deleteAll();

    testUser =
        User.builder()
            .email("pg.test@example.com")
            .passwordHash("hash")
            .fullName("Postgres Test User")
            .build();
    userRepository.save(testUser);

    activityRepository.save(
        Activity.builder()
            .user(testUser)
            .activityType(ActivityType.RUNNING)
            .source(ActivitySource.MANUAL)
            .startedAt(LocalDateTime.now().minusDays(2))
            .durationMinutes(60)
            .build());
    activityRepository.save(
        Activity.builder()
            .user(testUser)
            .activityType(ActivityType.CYCLING)
            .source(ActivitySource.IOT)
            .deviceId("device-1")
            .startedAt(LocalDateTime.now().minusDays(1))
            .durationMinutes(120)
            .build());
  }

  @Test
  void findByFilters_withAllFiltersNull_bindsNullParametersOnPostgres() {
    Page<Activity> page =
        activityRepository.findByFilters(
            testUser.getId(), null, null, null, null, PageRequest.of(0, 10));

    assertThat(page.getContent()).hasSize(2);
  }

  @Test
  void findByFilters_withTypeAndDateRange_filtersOnPostgres() {
    Page<Activity> page =
        activityRepository.findByFilters(
            testUser.getId(),
            LocalDateTime.now().minusDays(5),
            LocalDateTime.now(),
            ActivityType.RUNNING,
            null,
            PageRequest.of(0, 10));

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).getActivityType()).isEqualTo(ActivityType.RUNNING);
  }

  @Test
  void findDistinctActiveDates_castsToLocalDateOnPostgres() {
    List<LocalDate> dates =
        activityRepository.findDistinctActiveDatesByUserId(
            testUser.getId(), LocalDateTime.now().minusDays(10));

    assertThat(dates)
        .containsExactlyInAnyOrder(LocalDate.now().minusDays(2), LocalDate.now().minusDays(1));
  }
}
