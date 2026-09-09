package com.healthcare.activitytracker.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimezoneResolverTest {

  @Test
  void resolveZone_parsesValidIanaTimezone() {
    assertThat(TimezoneResolver.resolveZone("Asia/Kolkata")).isEqualTo(ZoneId.of("Asia/Kolkata"));
  }

  @Test
  void resolveZone_defaultsToUtc_whenNull() {
    assertThat(TimezoneResolver.resolveZone(null)).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void resolveZone_defaultsToUtc_whenBlank() {
    assertThat(TimezoneResolver.resolveZone("   ")).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void resolveZone_defaultsToUtc_whenUnrecognized() {
    assertThat(TimezoneResolver.resolveZone("Not/A/Zone")).isEqualTo(ZoneOffset.UTC);
  }
}
