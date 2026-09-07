package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.model.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private MilestoneMessageService milestoneMessageService;

  private NotificationService notificationService;

  @BeforeEach
  void setUp() {
    notificationService = new NotificationService(milestoneMessageService);
  }

  @Test
  void sendMilestoneNotification_doesNotThrow() {
    when(milestoneMessageService.milestoneMessage(anyInt())).thenReturn("Way to go!");
    User user =
        User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .passwordHash("hash")
            .fullName("Test User")
            .build();

    assertThatCode(() -> notificationService.sendMilestoneNotification(user, 7, UUID.randomUUID()))
        .doesNotThrowAnyException();
  }
}
