package com.healthcare.activitytracker.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  private final SecretKey accessKey;
  private final SecretKey refreshKey;
  private final long accessTokenExpiryMs;
  private final long refreshTokenExpiryMs;
  private final String issuer;
  private final String audience;

  public JwtUtil(
      @Value("${app.jwt.secret}") String accessSecret,
      @Value("${app.jwt.refresh-secret}") String refreshSecret,
      @Value("${app.jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
      @Value("${app.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs,
      @Value("${app.jwt.issuer:activity-tracker}") String issuer,
      @Value("${app.jwt.audience:activity-tracker-users}") String audience) {
    this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
    this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpiryMs = accessTokenExpiryMs;
    this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    this.issuer = issuer;
    this.audience = audience;
  }

  public static final String TOKEN_TYPE_ACCESS = "access";
  public static final String TOKEN_TYPE_REFRESH = "refresh";

  public String generateAccessToken(UUID userId, String email) {
    return buildToken(userId, email, TOKEN_TYPE_ACCESS, accessTokenExpiryMs, accessKey);
  }

  public String generateRefreshToken(UUID userId, String email) {
    return buildToken(userId, email, TOKEN_TYPE_REFRESH, refreshTokenExpiryMs, refreshKey);
  }

  private String buildToken(UUID userId, String email, String type, long expiryMs, SecretKey key) {
    Date now = new Date();
    return Jwts.builder()
        .subject(userId.toString())
        .claim("email", email)
        .claim("type", type)
        .issuer(issuer)
        .audience()
        .add(audience)
        .and()
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expiryMs))
        .signWith(key)
        .compact();
  }

  public String getTokenType(String token) {
    return parseAccessToken(token).get("type", String.class);
  }

  /** Parse and verify an access token using the access key. */
  public Claims parseAccessToken(String token) {
    return parseTokenWithKey(token, accessKey);
  }

  /** Parse and verify a refresh token using the refresh key. */
  public Claims parseRefreshToken(String token) {
    return parseTokenWithKey(token, refreshKey);
  }

  private Claims parseTokenWithKey(String token, SecretKey key) {
    return Jwts.parser()
        .verifyWith(key)
        .requireIssuer(issuer)
        .requireAudience(audience)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /**
   * @deprecated Use {@link #parseAccessToken(String)} or {@link #parseRefreshToken(String)}
   *     instead. Retained temporarily for backward compatibility during migration.
   */
  @Deprecated
  public Claims parseToken(String token) {
    return parseAccessToken(token);
  }

  public UUID getUserIdFromToken(String token) {
    return UUID.fromString(parseAccessToken(token).getSubject());
  }

  public UUID getUserIdFromRefreshToken(String token) {
    return UUID.fromString(parseRefreshToken(token).getSubject());
  }

  public boolean isAccessTokenValid(String token) {
    try {
      Claims claims = parseAccessToken(token);
      return claims.getExpiration().after(new Date());
    } catch (Exception e) {
      return false;
    }
  }

  public boolean isRefreshTokenValid(String token) {
    try {
      Claims claims = parseRefreshToken(token);
      return claims.getExpiration().after(new Date());
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * @deprecated Use {@link #isAccessTokenValid(String)} or {@link #isRefreshTokenValid(String)}
   *     instead.
   */
  @Deprecated
  public boolean isTokenValid(String token) {
    return isAccessTokenValid(token);
  }

  public long getAccessTokenExpiryMs() {
    return accessTokenExpiryMs;
  }
}
