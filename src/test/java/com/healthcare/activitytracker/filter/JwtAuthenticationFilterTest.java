package com.healthcare.activitytracker.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.service.TokenBlacklistService;
import com.healthcare.activitytracker.util.JwtUtil;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterTest {

  private JwtUtil jwtUtil;
  private TokenBlacklistService tokenBlacklistService;
  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    jwtUtil =
        new JwtUtil(
            "test-secret-key-for-unit-tests-must-be-at-least-32-chars",
            "test-refresh-secret-key-for-tests-must-be-at-least-32-chars",
            900_000L,
            604_800_000L,
            "activity-tracker",
            "activity-tracker-users");
    tokenBlacklistService = new TokenBlacklistService(null); // in-memory fallback
    filter = new JwtAuthenticationFilter(jwtUtil, tokenBlacklistService, new ObjectMapper());
  }

  @Test
  void doFilterInternal_proceedsWithNoAuthHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void doFilterInternal_returns401_whenTokenIsInvalid() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer invalid.token.here");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternal_proceedsAndSetsAuth_whenTokenIsValid() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = jwtUtil.generateAccessToken(userId, "test@example.com");

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verify(chain, times(1)).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void doFilterInternal_returns401_whenRefreshTokenUsedAsAccessToken() throws Exception {
    UUID userId = UUID.randomUUID();
    // Generate a refresh token — it should be rejected as an access token
    String refreshToken = jwtUtil.generateRefreshToken(userId, "test@example.com");

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + refreshToken);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void shouldNotFilter_returnsTrue_forLogoutPath() {
    // Logout must stay reachable with an invalid/expired token (idempotent 204 contract)
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
    request.setRequestURI("/api/v1/auth/logout");

    assertThat(filter.shouldNotFilter(request)).isTrue();

    MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/v1/activities");
    other.setRequestURI("/api/v1/activities");
    assertThat(filter.shouldNotFilter(other)).isFalse();
  }

  @Test
  void doFilterInternal_returns401_whenTokenIsRevoked() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = jwtUtil.generateAccessToken(userId, "test@example.com");

    // Revoke the token
    tokenBlacklistService.revoke(token, 900_000L);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
  }
}
