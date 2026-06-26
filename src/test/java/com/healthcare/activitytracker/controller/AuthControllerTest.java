package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.model.dto.AuthResponse;
import com.healthcare.activitytracker.model.dto.LoginRequest;
import com.healthcare.activitytracker.model.dto.RefreshRequest;
import com.healthcare.activitytracker.model.dto.RegisterRequest;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.EmailVerificationService;
import com.healthcare.activitytracker.service.PasswordResetService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean AuthService authService;
  @MockBean EmailVerificationService emailVerificationService;
  @MockBean PasswordResetService passwordResetService;
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
}
