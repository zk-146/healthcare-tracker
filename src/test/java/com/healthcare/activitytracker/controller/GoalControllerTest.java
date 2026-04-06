package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.GoalRequest;
import com.healthcare.activitytracker.model.dto.GoalResponse;
import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.service.GoalService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.util.Collections;
import java.util.List;
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
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, Collections.emptyList());
    return authentication(auth);
  }

  private GoalResponse sampleGoal() {
    return GoalResponse.builder()
        .id(UUID.randomUUID())
        .metric(GoalMetric.CALORIES_BURNED)
        .targetValue(500)
        .active(true)
        .build();
  }

  @Test
  void create_returns201_withCreatedGoal() throws Exception {
    GoalRequest request = GoalRequest.builder().metric(GoalMetric.CALORIES_BURNED).targetValue(500).build();
    when(goalService.create(any(), any())).thenReturn(sampleGoal());

    mockMvc
        .perform(
            post("/api/v1/goals")
                .with(uuidUser())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.metric").value("CALORIES_BURNED"))
        .andExpect(jsonPath("$.targetValue").value(500))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void create_returns401_whenUnauthenticated() throws Exception {
    GoalRequest request = GoalRequest.builder().metric(GoalMetric.STEPS).targetValue(10000).build();

    mockMvc
        .perform(
            post("/api/v1/goals")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listActive_returns200_withGoals() throws Exception {
    when(goalService.listActive(any())).thenReturn(List.of(sampleGoal()));

    mockMvc
        .perform(get("/api/v1/goals").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].metric").value("CALORIES_BURNED"));
  }

  @Test
  void getById_returns200_withGoal() throws Exception {
    UUID goalId = UUID.randomUUID();
    GoalResponse response = sampleGoal();
    when(goalService.getById(any(), eq(goalId))).thenReturn(response);

    mockMvc
        .perform(get("/api/v1/goals/{id}", goalId).with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metric").value("CALORIES_BURNED"));
  }

  @Test
  void getById_returns404_whenNotFound() throws Exception {
    UUID goalId = UUID.randomUUID();
    when(goalService.getById(any(), eq(goalId))).thenThrow(new ResourceNotFoundException("Goal not found"));

    mockMvc
        .perform(get("/api/v1/goals/{id}", goalId).with(uuidUser()))
        .andExpect(status().isNotFound());
  }

  @Test
  void update_returns200_withUpdatedGoal() throws Exception {
    UUID goalId = UUID.randomUUID();
    GoalRequest request = GoalRequest.builder().metric(GoalMetric.CALORIES_BURNED).targetValue(800).build();
    GoalResponse updated = GoalResponse.builder().id(goalId).metric(GoalMetric.CALORIES_BURNED)
        .targetValue(800).active(true).build();
    when(goalService.update(any(), eq(goalId), any())).thenReturn(updated);

    mockMvc
        .perform(
            put("/api/v1/goals/{id}", goalId)
                .with(uuidUser())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetValue").value(800));
  }

  @Test
  void deactivate_returns204_onSuccess() throws Exception {
    UUID goalId = UUID.randomUUID();
    doNothing().when(goalService).deactivate(any(), eq(goalId));

    mockMvc
        .perform(delete("/api/v1/goals/{id}", goalId).with(uuidUser()).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deactivate_returns404_whenNotFound() throws Exception {
    UUID goalId = UUID.randomUUID();
    doThrow(new ResourceNotFoundException("Goal not found")).when(goalService).deactivate(any(), eq(goalId));

    mockMvc
        .perform(delete("/api/v1/goals/{id}", goalId).with(uuidUser()).with(csrf()))
        .andExpect(status().isNotFound());
  }
}
