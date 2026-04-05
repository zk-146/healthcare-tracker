package com.healthcare.activitytracker.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.dto.ActivityRequest;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DeviceIdValidatorTest {

  private final DeviceIdValidator validator = new DeviceIdValidator();

  private ActivityRequest buildRequest(ActivitySource source, String deviceId) {
    ActivityRequest req = new ActivityRequest();
    req.setActivityType(ActivityType.RUNNING);
    req.setSource(source);
    req.setDeviceId(deviceId);
    req.setStartedAt(LocalDateTime.now().minusHours(1));
    return req;
  }

  @Test
  void isValid_returnsTrueForManualWithoutDeviceId() {
    assertThat(validator.isValid(buildRequest(ActivitySource.MANUAL, null), null)).isTrue();
  }

  @Test
  void isValid_returnsTrueForIotWithDeviceId() {
    assertThat(validator.isValid(buildRequest(ActivitySource.IOT, "device-123"), null)).isTrue();
  }

  @Test
  void isValid_returnsFalseForIotWithoutDeviceId() {
    assertThat(validator.isValid(buildRequest(ActivitySource.IOT, null), null)).isFalse();
  }

  @Test
  void isValid_returnsFalseForIotWithBlankDeviceId() {
    assertThat(validator.isValid(buildRequest(ActivitySource.IOT, "  "), null)).isFalse();
  }
}
