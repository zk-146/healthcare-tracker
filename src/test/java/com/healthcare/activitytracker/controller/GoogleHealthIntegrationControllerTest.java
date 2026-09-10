package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.model.enums.ConnectionStatus;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.GoogleHealthConnectionService;
import com.healthcare.activitytracker.service.GoogleHealthOAuthService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
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

@WebMvcTest(GoogleHealthIntegrationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class GoogleHealthIntegrationControllerTest {

  @Autowired MockMvc mockMvc;
  @MockBean GoogleHealthOAuthService oauthService;
  @MockBean GoogleHealthConnectionService connectionService;
  @MockBean GoogleHealthProperties properties;
  @MockBean AuthService authService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser(UUID userId) {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    return authentication(auth);
  }

  @Test
  void connect_returnsAuthorizationUrlWhenEnabled() throws Exception {
    UUID userId = UUID.randomUUID();
    when(properties.isEnabled()).thenReturn(true);
    when(oauthService.buildAuthorizationUrl(userId))
        .thenReturn("https://accounts.google.com/o/oauth2/v2/auth?state=abc");

    mockMvc
        .perform(get("/api/v1/integrations/google-health/connect").with(uuidUser(userId)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.authorizationUrl")
                .value("https://accounts.google.com/o/oauth2/v2/auth?state=abc"));
  }

  @Test
  void connect_returns503WhenIntegrationDisabled() throws Exception {
    when(properties.isEnabled()).thenReturn(false);

    mockMvc
        .perform(get("/api/v1/integrations/google-health/connect").with(uuidUser(UUID.randomUUID())))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void connect_requiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/integrations/google-health/connect"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void callback_completesConnectionAndReturnsSuccessMessage() throws Exception {
    when(properties.isEnabled()).thenReturn(true);

    mockMvc
        .perform(
            get("/api/v1/integrations/google-health/callback")
                .param("code", "auth-code")
                .param("state", "the-state"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string("Fitbit (Google Health) connected. You can close this window."));

    verify(connectionService).completeConnection("auth-code", "the-state");
  }

  @Test
  void callback_returnsBadRequestWhenGoogleReportsError() throws Exception {
    when(properties.isEnabled()).thenReturn(true);

    mockMvc
        .perform(
            get("/api/v1/integrations/google-health/callback").param("error", "access_denied"))
        .andExpect(status().isBadRequest())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string("Authorization failed: access_denied"));
  }

  @Test
  void callback_returns503WhenIntegrationDisabled() throws Exception {
    when(properties.isEnabled()).thenReturn(false);

    mockMvc
        .perform(get("/api/v1/integrations/google-health/callback").param("code", "auth-code"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void status_returnsConnectedDetailsWhenConnectionExists() throws Exception {
    UUID userId = UUID.randomUUID();
    LocalDateTime lastSynced = LocalDateTime.now().minusHours(2);
    GoogleHealthConnection connection =
        GoogleHealthConnection.builder()
            .status(ConnectionStatus.CONNECTED)
            .lastSyncedAt(lastSynced)
            .build();
    when(connectionService.findConnection(userId)).thenReturn(Optional.of(connection));

    mockMvc
        .perform(get("/api/v1/integrations/google-health/status").with(uuidUser(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(true))
        .andExpect(jsonPath("$.status").value("CONNECTED"));
  }

  @Test
  void status_returnsNotConnectedWhenNoConnection() throws Exception {
    UUID userId = UUID.randomUUID();
    when(connectionService.findConnection(userId)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/integrations/google-health/status").with(uuidUser(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connected").value(false));
  }

  @Test
  void disconnect_removesConnectionAndReturnsNoContent() throws Exception {
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/v1/integrations/google-health").with(uuidUser(userId)))
        .andExpect(status().isNoContent());

    verify(connectionService).disconnect(userId);
  }
}
