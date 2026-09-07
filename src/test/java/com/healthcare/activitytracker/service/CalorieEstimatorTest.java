package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ActivityType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CalorieEstimatorTest {

  private final CalorieEstimator estimator = new CalorieEstimator();

  private User userWithWeight(double weightKg) {
    return User.builder().weightKg(weightKg).build();
  }

  @Test
  void estimates_fromDurationAndWeight() {
    // 60 min WALKING (MET 3.5) at 80 kg: 60 * 3.5 * 3.5 * 80 / 200 = 294.0
    assertThat(estimator.estimateCalories(ActivityType.WALKING, 60, null, userWithWeight(80.0)))
        .hasValue(294.0);
  }

  @Test
  void appliesAgeFactor_forOlderUsers() {
    // Age 65: factor = 1 - 0.003 * (65 - 25) = 0.88 → 294 * 0.88 = 258.7 (1 dp)
    User user = User.builder().weightKg(80.0).dateOfBirth(LocalDate.now().minusYears(65)).build();
    assertThat(estimator.estimateCalories(ActivityType.WALKING, 60, null, user)).hasValue(258.7);
  }

  @Test
  void ageFactor_isFlooredAtMinimum() {
    // Age 90: raw factor 0.805 → floored at 0.85 → 294 * 0.85 = 249.9
    User user = User.builder().weightKg(80.0).dateOfBirth(LocalDate.now().minusYears(90)).build();
    assertThat(estimator.estimateCalories(ActivityType.WALKING, 60, null, user)).hasValue(249.9);
  }

  @Test
  void noAgeReduction_forYoungUsers() {
    User user = User.builder().weightKg(80.0).dateOfBirth(LocalDate.now().minusYears(20)).build();
    assertThat(estimator.estimateCalories(ActivityType.WALKING, 60, null, user)).hasValue(294.0);
  }

  @Test
  void derivesDurationFromDistance_forDistanceActivities() {
    // 5 km RUNNING at typical 10 km/h = 30 min; MET 9.8 at default 70 kg:
    // 30 * 9.8 * 3.5 * 70 / 200 = 360.15 → 360.2 (1 dp)
    assertThat(estimator.estimateCalories(ActivityType.RUNNING, null, 5.0, null)).hasValue(360.2);
  }

  @Test
  void defaultsWeight_whenUserHasNone() {
    // 30 min RUNNING, no user profile: default 70 kg → 360.2 as above
    assertThat(estimator.estimateCalories(ActivityType.RUNNING, 30, null, null)).hasValue(360.2);
  }

  @Test
  void empty_whenNoDurationAndNoDistance() {
    assertThat(estimator.estimateCalories(ActivityType.YOGA, null, null, userWithWeight(70.0)))
        .isEmpty();
  }

  @Test
  void empty_whenOnlyDistance_forNonDistanceActivity() {
    assertThat(
            estimator.estimateCalories(
                ActivityType.STRENGTH_TRAINING, null, 5.0, userWithWeight(70.0)))
        .isEmpty();
  }

  @Test
  void empty_whenTypeNull() {
    assertThat(estimator.estimateCalories(null, 30, null, userWithWeight(70.0))).isEmpty();
  }
}
