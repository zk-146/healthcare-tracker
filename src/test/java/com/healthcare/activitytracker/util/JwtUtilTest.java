package com.healthcare.activitytracker.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

  private JwtUtil jwtUtil;
  private final String accessSecret = "test-secret-key-for-unit-tests-must-be-at-least-32-chars";
  private final String refreshSecret =
      "test-refresh-secret-key-for-tests-must-be-at-least-32-chars";
  private final long accessExpiry = 900_000L;
  private final long refreshExpiry = 604_800_000L;

  @BeforeEach
  void setUp() {
    jwtUtil =
        new JwtUtil(
            accessSecret,
            refreshSecret,
            accessExpiry,
            refreshExpiry,
            "activity-tracker",
            "activity-tracker-users");
  }

  @Test
  void generateAccessToken_isValidAndContainsUserId() {
    UUID userId = UUID.randomUUID();
    String token = jwtUtil.generateAccessToken(userId, "test@example.com");

    assertThat(token).isNotBlank();
    assertThat(jwtUtil.isAccessTokenValid(token)).isTrue();
    assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(userId);
  }

  @Test
  void generateRefreshToken_isValidAndContainsUserId() {
    UUID userId = UUID.randomUUID();
    String token = jwtUtil.generateRefreshToken(userId, "test@example.com");

    assertThat(token).isNotBlank();
    assertThat(jwtUtil.isRefreshTokenValid(token)).isTrue();
    assertThat(jwtUtil.getUserIdFromRefreshToken(token)).isEqualTo(userId);
  }

  @Test
  void accessToken_cannotBeVerifiedAsRefreshToken() {
    UUID userId = UUID.randomUUID();
    String accessToken = jwtUtil.generateAccessToken(userId, "test@example.com");

    // An access token should NOT be valid when checked with the refresh key
    assertThat(jwtUtil.isRefreshTokenValid(accessToken)).isFalse();
  }

  @Test
  void refreshToken_cannotBeVerifiedAsAccessToken() {
    UUID userId = UUID.randomUUID();
    String refreshToken = jwtUtil.generateRefreshToken(userId, "test@example.com");

    // A refresh token should NOT be valid when checked with the access key
    assertThat(jwtUtil.isAccessTokenValid(refreshToken)).isFalse();
  }

  @Test
  void isAccessTokenValid_returnsFalseForExpiredToken() {
    JwtUtil shortLivedUtil =
        new JwtUtil(
            accessSecret,
            refreshSecret,
            1L,
            refreshExpiry,
            "activity-tracker",
            "activity-tracker-users"); // 1ms access expiry
    UUID userId = UUID.randomUUID();
    String token = shortLivedUtil.generateAccessToken(userId, "test@example.com");

    // Token should expire almost immediately
    try {
      Thread.sleep(10);
    } catch (InterruptedException ignored) {
    }

    assertThat(shortLivedUtil.isAccessTokenValid(token)).isFalse();
  }

  @Test
  void isAccessTokenValid_returnsFalseForTamperedToken() {
    UUID userId = UUID.randomUUID();
    String token = jwtUtil.generateAccessToken(userId, "test@example.com");
    String tampered = token.substring(0, token.length() - 5) + "XXXXX";

    assertThat(jwtUtil.isAccessTokenValid(tampered)).isFalse();
  }

  @Test
  void isAccessTokenValid_returnsFalseForNull() {
    assertThat(jwtUtil.isAccessTokenValid(null)).isFalse();
  }

  @Test
  void isAccessTokenValid_returnsFalseForBlankString() {
    assertThat(jwtUtil.isAccessTokenValid("")).isFalse();
  }

  @Test
  void getAccessTokenExpiryMs_returnsConfiguredValue() {
    assertThat(jwtUtil.getAccessTokenExpiryMs()).isEqualTo(accessExpiry);
  }
}
