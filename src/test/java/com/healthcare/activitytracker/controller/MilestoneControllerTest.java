package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.model.dto.MilestoneProgressResponse;
import com.healthcare.activitytracker.model.dto.MilestoneResponse;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.MilestoneService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MilestoneController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class MilestoneControllerTest {

  @Autowired MockMvc mockMvc;
  @MockBean MilestoneService milestoneService;
  @MockBean AuthService authService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, Collections.emptyList());
    return authentication(auth);
  }

  @Test
  void list_returns200_withEarnedMilestonesLongestFirst() throws Exception {
    when(milestoneService.getMilestones(any()))
        .thenReturn(
            List.of(
                MilestoneResponse.builder()
                    .milestoneDays(7)
                    .achievedAt(LocalDateTime.of(2026, 8, 20, 9, 0))
                    .build(),
                MilestoneResponse.builder()
                    .milestoneDays(3)
                    .achievedAt(LocalDateTime.of(2026, 8, 15, 9, 0))
                    .build()));

    mockMvc
        .perform(get("/api/v1/milestones").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].milestoneDays").value(7))
        .andExpect(jsonPath("$[1].milestoneDays").value(3));
  }

  @Test
  void list_returns200_withEmptyArray_whenNoneEarnedYet() throws Exception {
    when(milestoneService.getMilestones(any())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/milestones").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void list_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/milestones")).andExpect(status().isUnauthorized());
  }

  @Test
  void progress_returns200_withTheLadderAndNextRung() throws Exception {
    when(milestoneService.getProgress(any(), any()))
        .thenReturn(
            MilestoneProgressResponse.builder()
                .currentStreak(12)
                .nextThreshold(14)
                .daysRemaining(2)
                .progress(12 / 14.0)
                .ladder(List.of(3, 7, 14, 30, 60, 100, 365))
                .build());

    mockMvc
        .perform(get("/api/v1/milestones/progress").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStreak").value(12))
        .andExpect(jsonPath("$.nextThreshold").value(14))
        .andExpect(jsonPath("$.daysRemaining").value(2))
        .andExpect(jsonPath("$.ladder.length()").value(7));
  }

  @Test
  void progress_resolvesTheUserTimezoneHeader() throws Exception {
    when(milestoneService.getProgress(any(), any()))
        .thenReturn(MilestoneProgressResponse.builder().currentStreak(5).build());

    mockMvc
        .perform(
            get("/api/v1/milestones/progress")
                .header("X-User-Timezone", "America/New_York")
                .with(uuidUser()))
        .andExpect(status().isOk());

    verify(milestoneService).getProgress(any(), eq(ZoneId.of("America/New_York")));
  }

  @Test
  void progress_defaultsToUtc_whenTimezoneHeaderIsAbsent() throws Exception {
    when(milestoneService.getProgress(any(), any()))
        .thenReturn(MilestoneProgressResponse.builder().currentStreak(5).build());

    mockMvc.perform(get("/api/v1/milestones/progress").with(uuidUser())).andExpect(status().isOk());

    verify(milestoneService).getProgress(any(), eq(ZoneOffset.UTC));
  }

  @Test
  void progress_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/milestones/progress")).andExpect(status().isUnauthorized());
  }
}
