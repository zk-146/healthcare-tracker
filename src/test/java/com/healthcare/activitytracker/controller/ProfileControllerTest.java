package com.healthcare.activitytracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.SecurityConfig;
import com.healthcare.activitytracker.model.dto.ProfileResponse;
import com.healthcare.activitytracker.model.dto.ProfileUpdateRequest;
import com.healthcare.activitytracker.service.AuthService;
import com.healthcare.activitytracker.service.ProfileService;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import java.time.LocalDateTime;
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

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ProfileControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean ProfileService profileService;
  @MockBean AuthService authService;
  @MockBean JwtUtil jwtUtil;
  @MockBean TokenBlacklistService tokenBlacklistService;

  private static RequestPostProcessor uuidUser() {
    return uuidUser(UUID.randomUUID());
  }

  private static RequestPostProcessor uuidUser(UUID userId) {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    return authentication(auth);
  }

  private ProfileResponse mockProfile() {
    return ProfileResponse.builder()
        .id(UUID.randomUUID())
        .email("test@example.com")
        .fullName("Test User")
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  @Test
  void getProfile_returns200_withProfile() throws Exception {
    when(profileService.getProfile(any())).thenReturn(mockProfile());

    mockMvc
        .perform(get("/api/v1/profile").with(uuidUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("test@example.com"));
  }

  @Test
  void getProfile_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
  }

  @Test
  void updateProfile_returns200_withValidBody() throws Exception {
    when(profileService.updateProfile(any(), any())).thenReturn(mockProfile());

    ProfileUpdateRequest request = new ProfileUpdateRequest();
    request.setFullName("Updated Name");

    mockMvc
        .perform(
            put("/api/v1/profile")
                .with(uuidUser())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void updateProfile_returns400_whenFullNameTooLong() throws Exception {
    ProfileUpdateRequest request = new ProfileUpdateRequest();
    request.setFullName("A".repeat(151));

    mockMvc
        .perform(
            put("/api/v1/profile")
                .with(uuidUser())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateProfile_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/profile")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteAccount_returns204_andRevokesTokenAndDeletesData() throws Exception {
    UUID userId = UUID.randomUUID();
    // The real JwtAuthenticationFilter is part of the slice and sees the Bearer header,
    // so the mocked JwtUtil must accept the token
    when(jwtUtil.isAccessTokenValid("some-access-token")).thenReturn(true);
    when(jwtUtil.getTokenType("some-access-token")).thenReturn("access");
    when(jwtUtil.getUserIdFromToken("some-access-token")).thenReturn(userId);

    mockMvc
        .perform(
            delete("/api/v1/profile")
                .with(uuidUser(userId))
                .header("Authorization", "Bearer some-access-token"))
        .andExpect(status().isNoContent());

    verify(authService).logout("some-access-token");
    verify(profileService).deleteAccount(userId);
  }

  @Test
  void deleteAccount_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(delete("/api/v1/profile")).andExpect(status().isUnauthorized());
  }
}
