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
}
