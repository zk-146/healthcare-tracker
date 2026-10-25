package com.healthcare.activitytracker.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.dto.GoalRequest;
import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.model.enums.GoalPeriod;
import org.junit.jupiter.api.Test;

class GoalTargetValidatorTest {

  private final GoalTargetValidator validator = new GoalTargetValidator();

  private GoalRequest request(GoalMetric metric, GoalPeriod period, Double target) {
    GoalRequest request = new GoalRequest();
    request.setMetric(metric);
    request.setPeriod(period);
    request.setTargetValue(target);
    return request;
  }

  @Test
  void isValid_acceptsATargetInsideTheMetricCeiling() {
    assertThat(validator.isValid(request(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 150.0), null))
        .isTrue();
  }

  @Test
  void isValid_rejectsZeroAndNegativeTargets() {
    assertThat(validator.isValid(request(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 0.0), null))
        .isFalse();
    assertThat(validator.isValid(request(GoalMetric.MINUTES, GoalPeriod.WEEKLY, -1.0), null))
        .isFalse();
  }

  @Test
  void isValid_defersToNotNull_whenAnyFieldIsAbsent() {
    // Reporting these here too would place two messages on one field, and the details map
    // keeps only the last.
    assertThat(validator.isValid(request(GoalMetric.MINUTES, GoalPeriod.WEEKLY, null), null))
        .isTrue();
    assertThat(validator.isValid(request(null, GoalPeriod.WEEKLY, 150.0), null)).isTrue();
    assertThat(validator.isValid(request(GoalMetric.MINUTES, null, 150.0), null)).isTrue();
  }

  @Test
  void messageFor_scalesTheCeilingByPeriodLength() {
    // 1440 minutes a day: 1440 daily, 10080 weekly, 44640 over a 31-day month.
    assertThat(messageAt(GoalMetric.MINUTES, GoalPeriod.DAILY, 1_440)).isNull();
    assertThat(messageAt(GoalMetric.MINUTES, GoalPeriod.DAILY, 1_441)).isNotNull();

    assertThat(messageAt(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 10_080)).isNull();
    assertThat(messageAt(GoalMetric.MINUTES, GoalPeriod.WEEKLY, 10_081)).isNotNull();

    assertThat(messageAt(GoalMetric.MINUTES, GoalPeriod.MONTHLY, 44_640)).isNull();
    assertThat(messageAt(GoalMetric.MINUTES, GoalPeriod.MONTHLY, 44_641)).isNotNull();
  }

  @Test
  void messageFor_rejectsADailyTargetThatWouldPassAsMonthly() {
    // The point of scaling: 500,000 steps is a sane monthly target and an absurd daily one.
    assertThat(messageAt(GoalMetric.STEPS, GoalPeriod.MONTHLY, 500_000)).isNull();
    assertThat(messageAt(GoalMetric.STEPS, GoalPeriod.DAILY, 500_000)).isNotNull();
  }

  @Test
  void messageFor_namesTheUnitAndPeriodSoTheLimitIsActionable() {
    String message = messageAt(GoalMetric.DISTANCE_KM, GoalPeriod.WEEKLY, 999_999);

    assertThat(message).contains("7000").contains("km").contains("weekly");
  }

  @Test
  void messageFor_rendersTheBoundWithoutATrailingDecimal() {
    assertThat(messageAt(GoalMetric.CALORIES, GoalPeriod.DAILY, 20_000)).contains("10000");
    assertThat(messageAt(GoalMetric.CALORIES, GoalPeriod.DAILY, 20_000)).doesNotContain("10000.0");
  }

  private static String messageAt(GoalMetric metric, GoalPeriod period, double target) {
    return GoalTargetValidator.messageFor(target, metric, period);
  }
}
