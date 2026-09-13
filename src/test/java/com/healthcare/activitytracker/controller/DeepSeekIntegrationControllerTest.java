package com.healthcare.activitytracker.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.DeepSeekConnectionService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(DeepSeekIntegrationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class DeepSeekIntegrationControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean DeepSeekConnectionService connectionService;
  @MockBean AuthService authService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser(UUID userId) {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    return authentication(auth);
  }

  @Test
  void status_returnsConnectedTrue_whenAKeyIsOnFile() throws Exception {
    UUID userId = UUID.randomUUID();
    when(connectionService.isConnected(userId)).thenReturn(true);

    mockMvc
        .perform(get("/api/v1/integrations/deepseek/status").with(uuidUser(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));
  }

  @Test
  void status_returnsConnectedFalse_whenNoKeyIsOnFile() throws Exception {
    UUID userId = UUID.randomUUID();
    when(connectionService.isConnected(userId)).thenReturn(false);

    mockMvc
        .perform(get("/api/v1/integrations/deepseek/status").with(uuidUser(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(false));
  }

  @Test
  void status_requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/integrations/deepseek/status"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void save_encryptsAndStoresTheKey_thenReturnsConnectedTrue() throws Exception {
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/v1/integrations/deepseek")
                .with(uuidUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("apiKey", "sk-my-key"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true));

    verify(connectionService).saveApiKey(userId, "sk-my-key");
  }

  @Test
  void save_rejectsABlankKey() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/integrations/deepseek")
                .with(uuidUser(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("apiKey", "  "))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void disconnect_removesTheKeyAndReturnsNoContent() throws Exception {
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/v1/integrations/deepseek").with(uuidUser(userId)))
        .andExpect(status().isNoContent());

    verify(connectionService).disconnect(userId);
  }

  @Test
  void status_reportsInactive_whenTheServerUsesTheDefaultProvider() throws Exception {
    UUID userId = UUID.randomUUID();
    when(connectionService.isConnected(userId)).thenReturn(true);

    mockMvc
        .perform(get("/api/v1/integrations/deepseek/status").with(uuidUser(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true))
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void save_reportsInactive_whenTheServerUsesTheDefaultProvider() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/integrations/deepseek")
                .with(uuidUser(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("apiKey", "sk-my-key"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Nested
  @TestPropertySource(properties = "app.ai.provider=deepseek")
  class WhenTheServerUsesDeepSeek {

    // Shadow the outer fields: those are injected from the outer class's context, which has the
    // default provider, so using them would silently ignore this class's property override.
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void status_reportsActive() throws Exception {
      mockMvc
          .perform(get("/api/v1/integrations/deepseek/status").with(uuidUser(UUID.randomUUID())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void save_reportsActive() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/integrations/deepseek")
                  .with(uuidUser(UUID.randomUUID()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(java.util.Map.of("apiKey", "sk-my-key"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.active").value(true));
    }
  }

  @Nested
  @TestPropertySource(properties = "app.ai.provider=DeepSeek")
  class WhenTheProviderValueIsUnrecognized {

    // See WhenTheServerUsesDeepSeek for why these are redeclared.
    @Autowired MockMvc mockMvc;

    // Provider matching is exact (see NoopAiTextClient), so "DeepSeek" selects no client at all
    // and a saved key is not used; the status must not claim otherwise.
    @Test
    void status_reportsInactive() throws Exception {
      mockMvc
          .perform(get("/api/v1/integrations/deepseek/status").with(uuidUser(UUID.randomUUID())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.active").value(false));
    }
  }
}
