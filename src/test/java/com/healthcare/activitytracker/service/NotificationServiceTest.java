package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.NotificationResponse;
import com.healthcare.activitytracker.model.entity.Notification;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.NotificationType;
import com.healthcare.activitytracker.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository notificationRepository;

  private NotificationService notificationService;

  private User user;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    notificationService = new NotificationService(notificationRepository);
    user = User.builder().id(userId).email("user@example.com").fullName("Test User").build();
  }

  @Test
  void notifyMilestone_savesNotificationWithCorrectFields() {
    notificationService.notifyMilestone(user, 7);

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(captor.capture());

    Notification saved = captor.getValue();
    assertThat(saved.getUser()).isEqualTo(user);
    assertThat(saved.getType()).isEqualTo(NotificationType.STREAK_MILESTONE);
    assertThat(saved.getTitle()).isEqualTo("7-Day Streak!");
    assertThat(saved.getBody()).isNotBlank();
    assertThat(saved.isRead()).isFalse();
  }

  @Test
  void notifyMilestone_producesDistinctMessagesForEachThreshold() {
    int[] thresholds = {3, 7, 14, 30, 60, 100, 365};
    for (int days : thresholds) {
      reset(notificationRepository);
      notificationService.notifyMilestone(user, days);

      ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
      verify(notificationRepository).save(captor.capture());
      assertThat(captor.getValue().getTitle()).isEqualTo(days + "-Day Streak!");
      assertThat(captor.getValue().getBody()).isNotBlank();
    }
  }

  @Test
  void getNotifications_returnsMappedPage() {
    Notification n =
        Notification.builder()
            .id(UUID.randomUUID())
            .user(user)
            .type(NotificationType.STREAK_MILESTONE)
            .title("7-Day Streak!")
            .body("One week of consistent activity — great work!")
            .read(false)
            .createdAt(LocalDateTime.now())
            .build();
    Pageable pageable = PageRequest.of(0, 20);
    when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
        .thenReturn(new PageImpl<>(List.of(n)));

    Page<NotificationResponse> result = notificationService.getNotifications(userId, pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    NotificationResponse dto = result.getContent().get(0);
    assertThat(dto.getType()).isEqualTo(NotificationType.STREAK_MILESTONE);
    assertThat(dto.getTitle()).isEqualTo("7-Day Streak!");
    assertThat(dto.isRead()).isFalse();
  }

  @Test
  void countUnread_delegatesToRepository() {
    when(notificationRepository.countByUserIdAndReadFalse(userId)).thenReturn(5L);

    assertThat(notificationService.countUnread(userId)).isEqualTo(5L);
  }

  @Test
  void markRead_setsReadFlag() {
    UUID notifId = UUID.randomUUID();
    Notification n =
        Notification.builder()
            .id(notifId)
            .user(user)
            .type(NotificationType.STREAK_MILESTONE)
            .title("3-Day Streak!")
            .body("Keep it up!")
            .read(false)
            .build();
    when(notificationRepository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.of(n));

    notificationService.markRead(userId, notifId);

    assertThat(n.isRead()).isTrue();
    verify(notificationRepository).save(n);
  }

  @Test
  void markRead_throwsNotFound_whenNotificationMissing() {
    UUID notifId = UUID.randomUUID();
    when(notificationRepository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> notificationService.markRead(userId, notifId))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(notificationRepository, never()).save(any());
  }

  @Test
  void markAllRead_delegatesToRepository() {
    notificationService.markAllRead(userId);

    verify(notificationRepository).markAllReadForUser(userId);
  }
}
