package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.exception.CsvImportException;
import com.healthcare.activitytracker.model.dto.CsvImportResponse;
import com.healthcare.activitytracker.service.FitbitCsvImportService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ActivityImportController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ActivityImportControllerTest {

  @Autowired MockMvc mockMvc;
  @MockBean FitbitCsvImportService fitbitCsvImportService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, Collections.emptyList());
    return authentication(auth);
  }

  private MockMultipartFile csvFile() {
    String content =
        "Id,ActivityDate,TotalSteps,TotalDistance,VeryActiveMinutes,FairlyActiveMinutes,"
            + "LightlyActiveMinutes,Calories\n1503960366,4/12/2016,13162,8.5,25,13,328,1985\n";
    return new MockMultipartFile(
        "file", "dailyActivity_merged.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void importFitbit_returns200_onSuccess() throws Exception {
    when(fitbitCsvImportService.importDailyActivity(any(), any()))
        .thenReturn(
            CsvImportResponse.builder()
                .fileName("dailyActivity_merged.csv")
                .totalRows(1)
                .imported(1)
                .duplicatesSkipped(0)
                .failed(0)
                .errors(List.of())
                .build());

    mockMvc
        .perform(
            multipart("/api/v1/activities/import/fitbit")
                .file(csvFile())
                .with(uuidUser())
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void importFitbit_returns400_whenServiceRejectsFile() throws Exception {
    when(fitbitCsvImportService.importDailyActivity(any(), any()))
        .thenThrow(new CsvImportException("Missing required column 'TotalSteps'"));

    mockMvc
        .perform(
            multipart("/api/v1/activities/import/fitbit")
                .file(csvFile())
                .with(uuidUser())
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void importFitbit_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(multipart("/api/v1/activities/import/fitbit").file(csvFile()).with(csrf()))
        .andExpect(status().isUnauthorized());
  }
}
