package com.healthcare.activitytracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.activitytracker.config.GoogleHealthProperties;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Handles the Google OAuth 2.0 flow used to authorize Google Health API access: building the
 * consent URL, exchanging the authorization code for tokens, and refreshing access tokens.
 *
 * <p>The CSRF {@code state} is mapped to the initiating user id in memory between the redirect and
 * the callback (the callback is unauthenticated, since Google redirects the browser without a JWT).
 * An in-memory map is sufficient for this single-instance personal integration; a multi-instance
 * deployment would persist the mapping in a shared store instead.
 */
@Service
public class GoogleHealthOAuthService {

  private static final Logger log = LoggerFactory.getLogger(GoogleHealthOAuthService.class);

  private final GoogleHealthProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private final SecureRandom secureRandom = new SecureRandom();
  private final ConcurrentHashMap<String, UUID> stateToUser = new ConcurrentHashMap<>();

  public GoogleHealthOAuthService(GoogleHealthProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient = RestClient.create();
  }

  /**
   * Builds the Google consent URL, remembering which user the generated {@code state} belongs to.
   */
  public String buildAuthorizationUrl(UUID userId) {
    byte[] stateBytes = new byte[24];
    secureRandom.nextBytes(stateBytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
    stateToUser.put(state, userId);

    return UriComponentsBuilder.fromUriString(properties.getAuthUri())
        .queryParam("client_id", properties.getClientId())
        .queryParam("redirect_uri", properties.getRedirectUri())
        .queryParam("response_type", "code")
        .queryParam("scope", String.join(" ", properties.getScopes()))
        // access_type=offline + prompt=consent guarantees a refresh token is returned.
        .queryParam("access_type", "offline")
        .queryParam("prompt", "consent")
        .queryParam("include_granted_scopes", "true")
        .queryParam("state", state)
        .build()
        .toUriString();
  }

  /**
   * Verifies a callback {@code state} and returns the user id it was issued for, consuming it so it
   * cannot be replayed.
   *
   * @throws IllegalStateException if the state is unknown (mismatch / already used / possible CSRF)
   */
  public UUID consumeStateToUserId(String state) {
    UUID userId = state == null ? null : stateToUser.remove(state);
    if (userId == null) {
      throw new IllegalStateException("OAuth state mismatch — possible CSRF, aborting");
    }
    return userId;
  }

  /** Exchanges an authorization code for access + refresh tokens. */
  public TokenResponse exchangeCode(String code) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", code);
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("redirect_uri", properties.getRedirectUri());
    form.add("grant_type", "authorization_code");

    JsonNode body = postForm(form);
    return new TokenResponse(
        body.path("access_token").asText(),
        body.path("refresh_token").asText(null),
        LocalDateTime.now().plusSeconds(body.path("expires_in").asLong(3600)),
        body.path("scope").asText(null));
  }

  /**
   * Obtains a fresh access token using the stored refresh token. Google normally does not return a
   * new refresh token here, so the existing one is carried forward.
   *
   * @throws RefreshTokenRevokedException if Google rejects the refresh token (it has expired or
   *     been revoked — in Testing mode this happens roughly every 7 days)
   */
  public TokenResponse refresh(String refreshToken) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", properties.getClientId());
    form.add("client_secret", properties.getClientSecret());
    form.add("refresh_token", refreshToken);
    form.add("grant_type", "refresh_token");

    JsonNode body;
    try {
      body = postForm(form);
    } catch (org.springframework.web.client.RestClientResponseException e) {
      if (isInvalidGrant(e.getResponseBodyAsString())) {
        throw new RefreshTokenRevokedException("Refresh token revoked or expired", e);
      }
      throw e;
    }

    return new TokenResponse(
        body.path("access_token").asText(),
        body.path("refresh_token").asText(refreshToken),
        LocalDateTime.now().plusSeconds(body.path("expires_in").asLong(3600)),
        body.path("scope").asText(null));
  }

  private JsonNode postForm(MultiValueMap<String, String> form) {
    String response =
        restClient
            .post()
            .uri(properties.getTokenUri())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(form)
            .retrieve()
            .body(String.class);
    try {
      return objectMapper.readTree(response == null ? "{}" : response);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to parse Google token response", e);
    }
  }

  private boolean isInvalidGrant(String responseBody) {
    if (responseBody == null) {
      return false;
    }
    try {
      return "invalid_grant".equals(objectMapper.readTree(responseBody).path("error").asText());
    } catch (Exception e) {
      log.debug("Could not parse OAuth error body", e);
      return responseBody.contains("invalid_grant");
    }
  }

  /** Result of a successful token exchange or refresh. */
  public record TokenResponse(
      String accessToken, String refreshToken, LocalDateTime expiresAt, String scopes) {}

  /** Raised when Google rejects the refresh token, signalling that re-consent is required. */
  public static class RefreshTokenRevokedException extends RuntimeException {
    public RefreshTokenRevokedException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
