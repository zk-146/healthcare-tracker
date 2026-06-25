package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.healthcare.activitytracker.model.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

  private NotificationService notificationService;

  @BeforeEach
  void setUp() {
    notificationService = new NotificationService();
  }

  @Test
  void sendMilestoneNotification_doesNotThrow() {
    User user =
        User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .passwordHash("hash")
            .fullName("Test User")
            .build();

    assertThatCode(
            () ->
                notificationService.sendMilestoneNotification(
                    user, 7, UUID.randomUUID()))
        .doesNotThrowAnyException();
  }
}
