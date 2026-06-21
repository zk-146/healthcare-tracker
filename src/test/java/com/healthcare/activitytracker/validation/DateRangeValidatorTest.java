package com.healthcare.activitytracker.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.dto.ActivityRequest;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DateRangeValidatorTest {

  private final DateRangeValidator validator = new DateRangeValidator();

  private ActivityRequest buildRequest(LocalDateTime start, LocalDateTime end) {
    ActivityRequest req = new ActivityRequest();
    req.setActivityType(ActivityType.RUNNING);
    req.setSource(ActivitySource.MANUAL);
    req.setStartedAt(start);
    req.setEndedAt(end);
    return req;
  }

  @Test
  void isValid_returnsTrueWhenEndedAtIsNull() {
    ActivityRequest req = buildRequest(LocalDateTime.now().minusHours(1), null);
    assertThat(validator.isValid(req, null)).isTrue();
  }

  @Test
  void isValid_returnsTrueWhenEndedAtIsAfterStartedAt() {
    ActivityRequest req =
        buildRequest(LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
    assertThat(validator.isValid(req, null)).isTrue();
  }

  @Test
  void isValid_returnsFalseWhenEndedAtIsBeforeStartedAt() {
    ActivityRequest req =
        buildRequest(LocalDateTime.now().minusHours(1), LocalDateTime.now().minusHours(2));
    assertThat(validator.isValid(req, null)).isFalse();
  }

  @Test
  void isValid_returnsFalseWhenEndedAtEqualsStartedAt() {
    LocalDateTime time = LocalDateTime.now().minusHours(1);
    ActivityRequest req = buildRequest(time, time);
    assertThat(validator.isValid(req, null)).isFalse();
  }

  @Test
  void isValid_returnsTrueWhenStartedAtIsNull() {
    ActivityRequest req = buildRequest(null, LocalDateTime.now());
    assertThat(validator.isValid(req, null)).isTrue();
  }

  @Test
  void isValid_returnsFalseWhenDurationFarExceedsElapsed() {
    // 60 min elapsed, but 240 min declared — over-reporting beyond the 60-min tolerance.
    LocalDateTime start = LocalDateTime.now().minusHours(2);
    ActivityRequest req = buildRequest(start, start.plusMinutes(60));
    req.setDurationMinutes(240);
    assertThat(validator.isValid(req, null)).isFalse();
  }

  @Test
  void isValid_returnsFalseWhenDurationFarBelowElapsed() {
    // 120 min elapsed, but only 10 min declared — under-reporting beyond the 60-min tolerance.
    LocalDateTime start = LocalDateTime.now().minusHours(3);
    ActivityRequest req = buildRequest(start, start.plusMinutes(120));
    req.setDurationMinutes(10);
    assertThat(validator.isValid(req, null)).isFalse();
  }

  @Test
  void isValid_returnsTrueWhenDurationWithinTolerance() {
    // 60 min elapsed, 90 min declared — within the 60-min tolerance.
    LocalDateTime start = LocalDateTime.now().minusHours(2);
    ActivityRequest req = buildRequest(start, start.plusMinutes(60));
    req.setDurationMinutes(90);
    assertThat(validator.isValid(req, null)).isTrue();
  }
}
