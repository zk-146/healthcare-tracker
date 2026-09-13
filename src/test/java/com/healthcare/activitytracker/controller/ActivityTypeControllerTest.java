package com.healthcare.activitytracker.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.util.Collections;
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

@WebMvcTest(ActivityTypeController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ActivityTypeControllerTest {

  @Autowired MockMvc mockMvc;
  @MockBean AuthService authService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, Collections.emptyList());
    return authentication(auth);
  }

  @Test
  void list_returnsEveryEnumConstant_inDeclarationOrder() throws Exception {
    mockMvc
        .perform(get("/api/v1/activity-types").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(ActivityType.values().length))
        .andExpect(jsonPath("$[0].name").value("WALKING"))
        .andExpect(jsonPath("$[0].metValue").value(3.5));
  }

  @Test
  void list_derivesTitleCaseLabelFromUnderscoredName() throws Exception {
    mockMvc
        .perform(get("/api/v1/activity-types").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.name=='STRENGTH_TRAINING')].label").value("Strength Training"))
        .andExpect(jsonPath("$[?(@.name=='YOGA')].label").value("Yoga"));
  }

  @Test
  void list_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/activity-types")).andExpect(status().isUnauthorized());
  }
}
