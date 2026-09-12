package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.model.dto.DigestResponse;
import com.healthcare.activitytracker.model.dto.SummaryResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityDigestServiceTest {

  @Mock private SummaryService summaryService;
  @Mock private AiTextClient aiTextClient;

  private ActivityDigestService digestService;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    digestService = new ActivityDigestService(summaryService, aiTextClient);
  }

  private SummaryResponse summaryWithData() {
    return SummaryResponse.builder()
        .from(LocalDate.of(2026, 7, 6))
        .to(LocalDate.of(2026, 7, 11))
        .totalActivities(5)
        .totalDurationMinutes(240)
        .totalCaloriesBurned(1800.0)
        .totalDistanceKm(22.5)
        .totalSteps(41000)
        .streakDays(6)
        .averageDailyCalories(300.0)
        .bySource(Map.of("MANUAL", 5L))
        .byActivityType(
            List.of(
                SummaryResponse.ActivityTypeSummary.builder()
                    .type("RUNNING")
                    .source("MANUAL")
                    .count(3)
                    .totalMinutes(150)
                    .totalCalories(1200.0)
                    .build()))
        .build();
  }

  @Test
  void generatesWeeklyDigest_fromSummaryStats() {
    when(summaryService.getWeeklySummary(userId, ZoneOffset.UTC)).thenReturn(summaryWithData());
    when(aiTextClient.generate(eq(userId), any()))
        .thenReturn(Optional.of("You crushed it this week!"));

    DigestResponse digest = digestService.generateDigest(userId, "weekly", ZoneOffset.UTC);

    assertThat(digest.isAvailable()).isTrue();
    assertThat(digest.getDigest()).isEqualTo("You crushed it this week!");
    assertThat(digest.getPeriod()).isEqualTo("weekly");
    assertThat(digest.getFrom()).isEqualTo(LocalDate.of(2026, 7, 6));
    assertThat(digest.getTo()).isEqualTo(LocalDate.of(2026, 7, 11));
  }

  @Test
  void promptContainsStats_andNoPii() {
    when(summaryService.getWeeklySummary(userId, ZoneOffset.UTC)).thenReturn(summaryWithData());
    when(aiTextClient.generate(eq(userId), any())).thenReturn(Optional.of("ok"));

    digestService.generateDigest(userId, "weekly", ZoneOffset.UTC);

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(aiTextClient).generate(eq(userId), prompt.capture());
    assertThat(prompt.getValue())
        .contains("Total activities: 5")
        .contains("Current streak: 6 days")
        .contains("RUNNING")
        .doesNotContain(userId.toString());
  }

  @Test
  void fallsBack_whenAiClientUnavailable() {
    when(summaryService.getWeeklySummary(userId, ZoneOffset.UTC)).thenReturn(summaryWithData());
    when(aiTextClient.generate(eq(userId), any())).thenReturn(Optional.empty());

    DigestResponse digest = digestService.generateDigest(userId, "weekly", ZoneOffset.UTC);

    assertThat(digest.isAvailable()).isFalse();
    assertThat(digest.getDigest()).isEqualTo(ActivityDigestService.UNAVAILABLE_MESSAGE);
  }

  @Test
  void skipsModel_whenNoActivitiesInPeriod() {
    SummaryResponse empty =
        SummaryResponse.builder()
            .from(LocalDate.of(2026, 7, 6))
            .to(LocalDate.of(2026, 7, 11))
            .totalActivities(0)
            .bySource(Map.of())
            .byActivityType(List.of())
            .build();
    when(summaryService.getWeeklySummary(userId, ZoneOffset.UTC)).thenReturn(empty);

    DigestResponse digest = digestService.generateDigest(userId, "weekly", ZoneOffset.UTC);

    assertThat(digest.isAvailable()).isTrue();
    assertThat(digest.getDigest()).isEqualTo(ActivityDigestService.EMPTY_PERIOD_MESSAGE);
    verifyNoInteractions(aiTextClient);
  }

  @Test
  void selectsMonthlySummary_forMonthlyPeriod() {
    when(summaryService.getMonthlySummary(userId, ZoneOffset.UTC)).thenReturn(summaryWithData());
    when(aiTextClient.generate(eq(userId), any())).thenReturn(Optional.of("ok"));

    digestService.generateDigest(userId, "monthly", ZoneOffset.UTC);

    verify(summaryService).getMonthlySummary(userId, ZoneOffset.UTC);
  }

  @Test
  void rejectsUnknownPeriod() {
    assertThatThrownBy(() -> digestService.generateDigest(userId, "yearly", ZoneOffset.UTC))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("daily, weekly, monthly");
  }
}
