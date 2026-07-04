package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.model.dto.AuthResponse;
import com.healthcare.activitytracker.model.dto.LoginRequest;
import com.healthcare.activitytracker.model.dto.PasswordChangeRequest;
import com.healthcare.activitytracker.model.dto.RefreshRequest;
import com.healthcare.activitytracker.model.dto.RegisterRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean AuthService authService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private AuthResponse mockAuthResponse() {
    return AuthResponse.builder()
        .token("access-token")
        .refreshToken("refresh-token")
        .expiresIn(900L)
        .userId(UUID.randomUUID().toString())
        .email("test@example.com")
        .build();
  }

  @Test
  void register_returns201_onSuccess() throws Exception {
    RegisterRequest req = new RegisterRequest();
    req.setEmail("new@example.com");
    req.setPassword("StrongP@ss123");
    req.setFullName("Test User");

    when(authService.register(any())).thenReturn(mockAuthResponse());

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").value("access-token"));
  }

  @Test
  void register_returns400_whenPasswordTooWeak() throws Exception {
    RegisterRequest req = new RegisterRequest();
    req.setEmail("new@example.com");
    req.setPassword("weak");
    req.setFullName("Test User");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void register_returns400_whenEmailBlank() throws Exception {
    RegisterRequest req = new RegisterRequest();
    req.setEmail("");
    req.setPassword("StrongP@ss123");
    req.setFullName("Test User");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void login_returns200_onSuccess() throws Exception {
    LoginRequest req = new LoginRequest();
    req.setEmail("test@example.com");
    req.setPassword("password123");

    when(authService.login(any())).thenReturn(mockAuthResponse());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
  }

  @Test
  void refresh_returns400_whenTokenBlank() throws Exception {
    RefreshRequest req = new RefreshRequest();
    req.setRefreshToken("");

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void refresh_returns200_withValidToken() throws Exception {
    RefreshRequest req = new RefreshRequest();
    req.setRefreshToken("valid-refresh-token");

    when(authService.refresh(any())).thenReturn(mockAuthResponse());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk());
  }

  @Test
  void refresh_returns400_whenBodyMalformed() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid-json"))
        .andExpect(status().isBadRequest());
  }

  private static RequestPostProcessor uuidUser(UUID userId) {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    return authentication(auth);
  }

  @Test
  void changePassword_returns204_onSuccess() throws Exception {
    UUID userId = UUID.randomUUID();
    PasswordChangeRequest req = new PasswordChangeRequest();
    req.setCurrentPassword("OldP@ssword123");
    req.setNewPassword("NewP@ssword456");

    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .with(uuidUser(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isNoContent());

    verify(authService).changePassword(eq(userId), eq("OldP@ssword123"), eq("NewP@ssword456"));
  }

  @Test
  void changePassword_returns400_whenNewPasswordWeak() throws Exception {
    PasswordChangeRequest req = new PasswordChangeRequest();
    req.setCurrentPassword("OldP@ssword123");
    req.setNewPassword("weak");

    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .with(uuidUser(UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void changePassword_returns401_whenUnauthenticated() throws Exception {
    PasswordChangeRequest req = new PasswordChangeRequest();
    req.setCurrentPassword("OldP@ssword123");
    req.setNewPassword("NewP@ssword456");

    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logout_returns204_evenWithoutAuthHeader() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
  }

  @Test
  void logout_returns204_withInvalidToken() throws Exception {
    // The JWT filter skips /logout, so an expired/garbage token must still yield 204
    mockMvc
        .perform(post("/api/v1/auth/logout").header("Authorization", "Bearer garbage.token.here"))
        .andExpect(status().isNoContent());

    verify(authService).logout("garbage.token.here");
  }
}
