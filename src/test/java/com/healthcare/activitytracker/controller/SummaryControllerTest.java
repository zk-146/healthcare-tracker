package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.model.dto.SummaryResponse;
import com.healthcare.activitytracker.service.SummaryService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SummaryController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SummaryControllerTest {

  @Autowired MockMvc mockMvc;
  @MockBean SummaryService summaryService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, Collections.emptyList());
    return authentication(auth);
  }

  private SummaryResponse emptySummary() {
    return SummaryResponse.builder()
        .from(LocalDate.now().minusDays(7))
        .to(LocalDate.now())
        .totalActivities(0)
        .bySource(Map.of())
        .byActivityType(List.of())
        .build();
  }

  @Test
  void getSummary_returns200_withDateRange() throws Exception {
    when(summaryService.getSummary(any(), any(), any(), any())).thenReturn(emptySummary());

    mockMvc
        .perform(
            get("/api/v1/summary")
                .with(uuidUser())
                .param("from", "2024-01-01")
                .param("to", "2024-01-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalActivities").value(0));
  }

  @Test
  void getSummary_returns400_whenMissingFromParam() throws Exception {
    mockMvc
        .perform(get("/api/v1/summary").with(uuidUser()).param("to", "2024-01-31"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSummary_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/v1/summary").param("from", "2024-01-01").param("to", "2024-01-31"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getDaily_returns200() throws Exception {
    when(summaryService.getDailySummary(any(), any())).thenReturn(emptySummary());

    mockMvc.perform(get("/api/v1/summary/daily").with(uuidUser())).andExpect(status().isOk());
  }

  @Test
  void getWeekly_returns200() throws Exception {
    when(summaryService.getWeeklySummary(any(), any())).thenReturn(emptySummary());

    mockMvc.perform(get("/api/v1/summary/weekly").with(uuidUser())).andExpect(status().isOk());
  }

  @Test
  void getMonthly_returns200() throws Exception {
    when(summaryService.getMonthlySummary(any(), any())).thenReturn(emptySummary());

    mockMvc.perform(get("/api/v1/summary/monthly").with(uuidUser())).andExpect(status().isOk());
  }

  @Test
  void getSummary_withTimezoneHeader_returns200() throws Exception {
    when(summaryService.getSummary(any(), any(), any(), any())).thenReturn(emptySummary());

    mockMvc
        .perform(
            get("/api/v1/summary")
                .with(uuidUser())
                .param("from", "2024-01-01")
                .param("to", "2024-01-31")
                .header("X-User-Timezone", "America/New_York"))
        .andExpect(status().isOk());
  }

  @Test
  void getSummary_withInvalidTimezone_stillReturns200() throws Exception {
    when(summaryService.getSummary(any(), any(), any(), any())).thenReturn(emptySummary());

    mockMvc
        .perform(
            get("/api/v1/summary")
                .with(uuidUser())
                .param("from", "2024-01-01")
                .param("to", "2024-01-31")
                .header("X-User-Timezone", "Not/A/Zone"))
        .andExpect(status().isOk());
  }
}
