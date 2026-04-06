package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.model.dto.NotificationResponse;
import com.healthcare.activitytracker.model.enums.NotificationType;
import com.healthcare.activitytracker.service.NotificationService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class NotificationControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean NotificationService notificationService;

  @MockBean JwtUtil jwtUtil;

  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, Collections.emptyList());
    return authentication(auth);
  }

  private NotificationResponse sampleNotification() {
    return NotificationResponse.builder()
        .id(UUID.randomUUID())
        .type(NotificationType.STREAK_MILESTONE)
        .title("7-Day Streak!")
        .body("One week of consistent activity — great work!")
        .read(false)
        .createdAt(LocalDateTime.now())
        .build();
  }

  @Test
  void list_returns200_withNotifications() throws Exception {
    when(notificationService.getNotifications(any(), any()))
        .thenReturn(new PageImpl<>(List.of(sampleNotification())));

    mockMvc
        .perform(get("/api/v1/notifications").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].title").value("7-Day Streak!"))
        .andExpect(jsonPath("$.content[0].read").value(false));
  }

  @Test
  void list_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
  }

  @Test
  void unreadCount_returns200_withCount() throws Exception {
    when(notificationService.countUnread(any())).thenReturn(3L);

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(content().string("3"));
  }

  @Test
  void markRead_returns204_onSuccess() throws Exception {
    UUID notifId = UUID.randomUUID();
    doNothing().when(notificationService).markRead(any(), any());

    mockMvc
        .perform(
            post("/api/v1/notifications/{id}/read", notifId).with(uuidUser()).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void markAllRead_returns204_onSuccess() throws Exception {
    doNothing().when(notificationService).markAllRead(any());

    mockMvc
        .perform(post("/api/v1/notifications/read-all").with(uuidUser()).with(csrf()))
        .andExpect(status().isNoContent());
  }
}
