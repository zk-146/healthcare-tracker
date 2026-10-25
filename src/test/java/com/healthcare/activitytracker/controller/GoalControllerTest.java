package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.exception.ResourceConflictException;
import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.GoalResponse;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.GoalService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(GoalController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class GoalControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean GoalService goalService;
  @MockBean AuthService authService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, Collections.emptyList());
    return authentication(auth);
  }

  private static GoalResponse goalResponse() {
    return GoalResponse.builder()
        .id(UUID.randomUUID())
        .metric("MINUTES")
        .period("WEEKLY")
        .targetValue(150.0)
        .unit("minutes")
        .progress(
            GoalResponse.Progress.builder()
                .from(LocalDate.of(2026, 9, 7))
                .to(LocalDate.of(2026, 9, 9))
                .currentValue(60)
                .percentComplete(40)
                .daysElapsed(3)
                .daysTotal(7)
                .daysRemaining(4)
                .expectedByNow(64.3)
                .onTrack(false)
                .build())
        .build();
  }

  private String body(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  // ------------------------------------------------------------------ list

  @Test
  void list_returns200_withGoalsAndProgress() throws Exception {
    when(goalService.getGoals(any(), any())).thenReturn(List.of(goalResponse()));

    mockMvc
        .perform(get("/api/v1/goals").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].metric").value("MINUTES"))
        .andExpect(jsonPath("$[0].unit").value("minutes"))
        .andExpect(jsonPath("$[0].progress.expectedByNow").value(64.3))
        .andExpect(jsonPath("$[0].progress.onTrack").value(false));
  }

  @Test
  void list_returns200_withEmptyArray_whenNoGoalsSet() throws Exception {
    when(goalService.getGoals(any(), any())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/goals").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void list_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/goals")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------- create

  @Test
  void create_returns201_withTheCreatedGoal() throws Exception {
    when(goalService.createGoal(any(), any(), any())).thenReturn(goalResponse());

    mockMvc
        .perform(
            post("/api/v1/goals")
                .with(uuidUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("metric", "MINUTES", "period", "WEEKLY", "targetValue", 150))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetValue").value(150.0));
  }

  @Test
  void create_returns400_andNamesTargetValue_whenTargetIsNotPositive() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/goals")
                .with(uuidUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("metric", "MINUTES", "period", "WEEKLY", "targetValue", 0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.targetValue").value("Target must be greater than 0"));

    verify(goalService, never()).createGoal(any(), any(), any());
  }

  @Test
  void create_returns400_andNamesTargetValue_whenTargetExceedsTheMetricCeiling() throws Exception {
    // 100,000 steps/day * 7 = 700,000 weekly ceiling.
    mockMvc
        .perform(
            post("/api/v1/goals")
                .with(uuidUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(Map.of("metric", "STEPS", "period", "WEEKLY", "targetValue", 700_001))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.targetValue").exists());

    verify(goalService, never()).createGoal(any(), any(), any());
  }

  @Test
  void create_returns400_whenMetricIsMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/goals")
                .with(uuidUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("period", "WEEKLY", "targetValue", 150))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.metric").exists());
  }

  @Test
  void create_returns400_whenMetricIsNotAKnownvalue() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/goals")
                .with(uuidUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("metric", "PUSHUPS", "period", "WEEKLY", "targetValue", 150))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_returns409_whenAGoalAlreadyCoversThatMetricAndPeriod() throws Exception {
    when(goalService.createGoal(any(), any(), any()))
        .thenThrow(new ResourceConflictException("A weekly minutes goal already exists."));

    mockMvc
        .perform(
            post("/api/v1/goals")
                .with(uuidUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("metric", "MINUTES", "period", "WEEKLY", "targetValue", 150))))
        .andExpect(status().isConflict());
  }

  @Test
  void create_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("metric", "MINUTES", "period", "WEEKLY", "targetValue", 150))))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------- update

  @Test
  void update_returns200_withTheUpdatedGoal() throws Exception {
    when(goalService.updateGoal(any(), any(), any(), any())).thenReturn(goalResponse());

    mockMvc
        .perform(
            put("/api/v1/goals/" + UUID.randomUUID())
                .with(uuidUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("targetValue", 200))))
        .andExpect(status().isOk());
  }

  @Test
  void update_returns404_whenTheGoalIsNotTheCallers() throws Exception {
    when(goalService.updateGoal(any(), any(), any(), any()))
        .thenThrow(new ResourceNotFoundException("Goal not found"));

    mockMvc
        .perform(
            put("/api/v1/goals/" + UUID.randomUUID())
                .with(uuidUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("targetValue", 200))))
        .andExpect(status().isNotFound());
  }

  @Test
  void update_returns400_whenTargetIsAbsent() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/goals/" + UUID.randomUUID())
                .with(uuidUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.targetValue").exists());
  }

  @Test
  void update_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/goals/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("targetValue", 200))))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------- delete

  @Test
  void delete_returns204_andRetiresTheGoal() throws Exception {
    UUID goalId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/v1/goals/" + goalId).with(uuidUser()))
        .andExpect(status().isNoContent());

    verify(goalService).deleteGoal(any(), eq(goalId));
  }

  @Test
  void delete_returns404_whenTheGoalIsAlreadyRetired() throws Exception {
    doThrow(new ResourceNotFoundException("Goal not found"))
        .when(goalService)
        .deleteGoal(any(), any());

    mockMvc
        .perform(delete("/api/v1/goals/" + UUID.randomUUID()).with(uuidUser()))
        .andExpect(status().isNotFound());
  }

  @Test
  void delete_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(delete("/api/v1/goals/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }
}
