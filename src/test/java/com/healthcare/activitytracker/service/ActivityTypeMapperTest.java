package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.enums.ActivityType;
import org.junit.jupiter.api.Test;

class ActivityTypeMapperTest {

  private final ActivityTypeMapper mapper = new ActivityTypeMapper();

  @Test
  void mapsKnownTypesCaseInsensitively() {
    assertThat(mapper.map("RUN")).isEqualTo(ActivityType.RUNNING);
    assertThat(mapper.map("run")).isEqualTo(ActivityType.RUNNING);
    assertThat(mapper.map(" Walk ")).isEqualTo(ActivityType.WALKING);
    assertThat(mapper.map("BIKE")).isEqualTo(ActivityType.CYCLING);
    assertThat(mapper.map("weights")).isEqualTo(ActivityType.STRENGTH_TRAINING);
  }

  @Test
  void unknownTypeFallsBackToOther() {
    assertThat(mapper.map("KITESURFING")).isEqualTo(ActivityType.OTHER);
  }

  @Test
  void nullOrBlankFallsBackToOther() {
    assertThat(mapper.map(null)).isEqualTo(ActivityType.OTHER);
    assertThat(mapper.map("")).isEqualTo(ActivityType.OTHER);
    assertThat(mapper.map("   ")).isEqualTo(ActivityType.OTHER);
  }
}
