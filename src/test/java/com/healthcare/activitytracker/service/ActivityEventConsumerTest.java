package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.healthcare.activitytracker.model.entity.StreakMilestone;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityEventConsumerTest {

  @Mock private SummaryService summaryService;

  @Mock private StreakMilestoneRepository milestoneRepository;

  @Mock private UserRepository userRepository;

  @Mock private NotificationService notificationService;

  private ActivityEventConsumer consumer;

  private final UUID userId = UUID.randomUUID();
  private final UUID activityId = UUID.randomUUID();

  private ActivityCreatedEvent event;
  private User user;

  @BeforeEach
  void setUp() {
    consumer =
        new ActivityEventConsumer(
            summaryService, milestoneRepository, userRepository, notificationService);

    event =
        ActivityCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("ACTIVITY_CREATED")
            .occurredAt(LocalDateTime.now())
            .activityId(activityId)
            .userId(userId)
            .activityType(ActivityType.RUNNING)
            .source(ActivitySource.MANUAL)
            .durationMinutes(30)
            .startedAt(LocalDateTime.now())
            .build();

    user =
        User.builder()
            .id(userId)
            .email("test@example.com")
            .passwordHash("hash")
            .fullName("Test User")
            .build();
  }

  @Test
  void savesNewMilestone_whenStreakHitsThreshold() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(3);
    when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, 3)).thenReturn(false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    consumer.onActivityCreated(event, 0, 0L);

    ArgumentCaptor<StreakMilestone> captor = ArgumentCaptor.forClass(StreakMilestone.class);
    verify(milestoneRepository).save(captor.capture());

    StreakMilestone saved = captor.getValue();
    assertThat(saved.getMilestoneDays()).isEqualTo(3);
    assertThat(saved.getTriggeringActivityId()).isEqualTo(activityId);
    assertThat(saved.getUser()).isEqualTo(user);

    verify(notificationService).sendMilestoneNotification(user, 3, activityId);
  }

  @Test
  void doesNotSave_whenMilestoneAlreadyExists() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(3);
    when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, 3)).thenReturn(true);

    consumer.onActivityCreated(event, 0, 0L);

    verify(milestoneRepository, never()).save(any());
    verify(userRepository, never()).findById(any());
    verify(notificationService, never()).sendMilestoneNotification(any(), anyInt(), any());
  }

  @Test
  void doesNotSave_whenStreakBelowLowestThreshold() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(2);

    consumer.onActivityCreated(event, 0, 0L);

    verify(milestoneRepository, never()).save(any());
    verify(userRepository, never()).findById(any());
    verify(notificationService, never()).sendMilestoneNotification(any(), anyInt(), any());
  }

  /**
   * A streak of 5 is past the 3-day threshold without equalling any threshold. The milestone is
   * still earned — matching on equality alone would lose it.
   */
  @Test
  void awardsCrossedThreshold_whenStreakSitsBetweenThresholds() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(5);
    when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, 3)).thenReturn(false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    consumer.onActivityCreated(event, 0, 0L);

    ArgumentCaptor<StreakMilestone> captor = ArgumentCaptor.forClass(StreakMilestone.class);
    verify(milestoneRepository).save(captor.capture());
    assertThat(captor.getValue().getMilestoneDays()).isEqualTo(3);
    verify(notificationService).sendMilestoneNotification(user, 3, activityId);
  }

  /**
   * Regression: a bulk CSV import or the Google Health initial backfill commits every activity
   * before any event is consumed, so the streak arrives at its final value rather than passing
   * through each threshold on consecutive days. Matching on equality awarded nothing at all for a
   * 31-day backfill, because 31 is not itself a threshold.
   */
  @Test
  void awardsEveryCrossedThreshold_whenBackfillJumpsTheStreak() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(31);
    when(milestoneRepository.existsByUserIdAndMilestoneDays(eq(userId), anyInt())).thenReturn(false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    consumer.onActivityCreated(event, 0, 0L);

    ArgumentCaptor<StreakMilestone> captor = ArgumentCaptor.forClass(StreakMilestone.class);
    verify(milestoneRepository, times(4)).save(captor.capture());

    assertThat(captor.getAllValues())
        .extracting(StreakMilestone::getMilestoneDays)
        .containsExactly(3, 7, 14, 30);

    // One notification only, for the highest crossed — not four.
    verify(notificationService, times(1)).sendMilestoneNotification(any(), anyInt(), any());
    verify(notificationService).sendMilestoneNotification(user, 30, activityId);
  }

  @Test
  void skipsAlreadyEarnedThresholds_whenBackfillingOverExistingMilestones() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(30);
    when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, 3)).thenReturn(true);
    when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, 7)).thenReturn(true);
    when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, 14)).thenReturn(false);
    when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, 30)).thenReturn(false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    consumer.onActivityCreated(event, 0, 0L);

    ArgumentCaptor<StreakMilestone> captor = ArgumentCaptor.forClass(StreakMilestone.class);
    verify(milestoneRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(StreakMilestone::getMilestoneDays)
        .containsExactly(14, 30);

    verify(notificationService).sendMilestoneNotification(user, 30, activityId);
  }

  /**
   * Day-to-day use: the streak lands exactly on a threshold with all lower ones already earned, so
   * exactly one milestone is saved and one notification sent.
   */
  @Test
  void savesCorrectMilestone_forEachThreshold() {
    int[] thresholds = {3, 7, 14, 30, 60, 100, 365};
    for (int i = 0; i < thresholds.length; i++) {
      int threshold = thresholds[i];
      reset(summaryService, milestoneRepository, userRepository, notificationService);

      when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(threshold);
      for (int j = 0; j < i; j++) {
        when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, thresholds[j]))
            .thenReturn(true);
      }
      when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, threshold)).thenReturn(false);
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      consumer.onActivityCreated(event, 0, 0L);

      ArgumentCaptor<StreakMilestone> captor = ArgumentCaptor.forClass(StreakMilestone.class);
      verify(milestoneRepository).save(captor.capture());
      assertThat(captor.getValue().getMilestoneDays()).isEqualTo(threshold);
      verify(notificationService).sendMilestoneNotification(user, threshold, activityId);
    }
  }
}
