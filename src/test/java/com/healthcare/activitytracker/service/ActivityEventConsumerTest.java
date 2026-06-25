package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
  void doesNotSave_whenStreakIsNotAMilestone() {
    when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(5);

    consumer.onActivityCreated(event, 0, 0L);

    verify(milestoneRepository, never()).existsByUserIdAndMilestoneDays(any(), any());
    verify(milestoneRepository, never()).save(any());
    verify(notificationService, never()).sendMilestoneNotification(any(), anyInt(), any());
  }

  @Test
  void savesCorrectMilestone_forEachThreshold() {
    for (int threshold : new int[] {7, 14, 30, 60, 100, 365}) {
      reset(summaryService, milestoneRepository, userRepository, notificationService);

      when(summaryService.getCurrentStreak(userId, ZoneOffset.UTC)).thenReturn(threshold);
      when(milestoneRepository.existsByUserIdAndMilestoneDays(userId, threshold)).thenReturn(false);
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      consumer.onActivityCreated(event, 0, 0L);

      ArgumentCaptor<StreakMilestone> captor = ArgumentCaptor.forClass(StreakMilestone.class);
      verify(milestoneRepository).save(captor.capture());
      assertThat(captor.getValue().getMilestoneDays()).isEqualTo(threshold);
    }
  }
}
