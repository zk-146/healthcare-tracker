package com.healthcare.activitytracker.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MilestoneThresholdsTest {

  @Test
  void all_holdsTheLadderInAscendingOrder() {
    assertThat(MilestoneThresholds.ALL).containsExactly(3, 7, 14, 30, 60, 100, 365);
    assertThat(MilestoneThresholds.ALL).isSorted();
  }

  @Test
  void all_isImmutable() {
    assertThatThrownBy(() -> MilestoneThresholds.ALL.add(500))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void next_returnsFirstThresholdStrictlyAboveTheStreak() {
    assertThat(MilestoneThresholds.next(0)).contains(3);
    assertThat(MilestoneThresholds.next(12)).contains(14);
  }

  @Test
  void next_advancesPastAThresholdTheStreakHasExactlyReached() {
    assertThat(MilestoneThresholds.next(3)).contains(7);
  }

  @Test
  void next_isEmptyOnceTheLadderIsExhausted() {
    assertThat(MilestoneThresholds.next(365)).isEmpty();
    assertThat(MilestoneThresholds.next(400)).isEmpty();
  }

  @Test
  void next_treatsNegativeStreakAsBelowTheFirstRung() {
    assertThat(MilestoneThresholds.next(-1)).isEqualTo(Optional.of(3));
  }

  @Test
  void all_matchesTheListTheConsumerPreviouslyOwned() {
    assertThat(MilestoneThresholds.ALL).isEqualTo(List.of(3, 7, 14, 30, 60, 100, 365));
  }
}
