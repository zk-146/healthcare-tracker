package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.model.dto.ActivityRequest;
import com.healthcare.activitytracker.model.dto.ActivityResponse;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.service.ActivityService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ActivityController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ActivityControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean ActivityService activityService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  /**
   * Creates a RequestPostProcessor that sets the principal to a UUID, matching
   * JwtAuthenticationFilter behaviour.
   */
  private static RequestPostProcessor uuidUser() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, Collections.emptyList());
    return authentication(auth);
  }

  private ActivityResponse mockResponse() {
    return ActivityResponse.builder()
        .id(UUID.randomUUID())
        .activityType(ActivityType.RUNNING)
        .source(ActivitySource.MANUAL)
        .startedAt(LocalDateTime.now().minusHours(1))
        .createdAt(LocalDateTime.now())
        .build();
  }

  private ActivityRequest validRequest() {
    ActivityRequest req = new ActivityRequest();
    req.setActivityType(ActivityType.RUNNING);
    req.setSource(ActivitySource.MANUAL);
    req.setStartedAt(LocalDateTime.now().minusHours(1));
    return req;
  }

  @Test
  void createActivity_returns201_onSuccess() throws Exception {
    when(activityService.createActivity(any(), any())).thenReturn(mockResponse());

    mockMvc
        .perform(
            post("/api/v1/activities")
                .with(uuidUser())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
        .andExpect(status().isCreated());
  }

  @Test
  void createActivity_returns400_whenNegativeCalories() throws Exception {
    ActivityRequest req = validRequest();
    req.setCaloriesBurned(-100.0);

    mockMvc
        .perform(
            post("/api/v1/activities")
                .with(uuidUser())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createActivity_returns400_whenEndedAtBeforeStartedAt() throws Exception {
    ActivityRequest req = validRequest();
    req.setStartedAt(LocalDateTime.now().minusHours(1));
    req.setEndedAt(LocalDateTime.now().minusHours(2));

    mockMvc
        .perform(
            post("/api/v1/activities")
                .with(uuidUser())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createActivity_returns400_whenIotWithoutDeviceId() throws Exception {
    ActivityRequest req = validRequest();
    req.setSource(ActivitySource.IOT);
    req.setDeviceId(null);

    mockMvc
        .perform(
            post("/api/v1/activities")
                .with(uuidUser())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listActivities_returns200() throws Exception {
    when(activityService.getActivities(any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(mockResponse())));

    mockMvc.perform(get("/api/v1/activities").with(uuidUser())).andExpect(status().isOk());
  }

  @Test
  void listActivities_withDateRange_returns200() throws Exception {
    when(activityService.getActivities(any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(mockResponse())));

    mockMvc
        .perform(
            get("/api/v1/activities")
                .with(uuidUser())
                .param("from", "2024-01-01")
                .param("to", "2024-12-31"))
        .andExpect(status().isOk());
  }
}
