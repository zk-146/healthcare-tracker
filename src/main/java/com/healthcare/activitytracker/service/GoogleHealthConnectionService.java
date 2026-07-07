package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ConnectionStatus;
import com.healthcare.activitytracker.repository.GoogleHealthConnectionRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import com.healthcare.activitytracker.service.GoogleHealthOAuthService.RefreshTokenRevokedException;
import com.healthcare.activitytracker.service.GoogleHealthOAuthService.TokenResponse;
import com.healthcare.activitytracker.util.TokenCipher;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the lifecycle of a user's Google Health connection: storing tokens after consent,
 * supplying a valid (refreshed) access token to the sync job, and handling refresh-token revocation
 * by flipping the connection to {@link ConnectionStatus#NEEDS_RECONNECT} and notifying the owner.
 */
@Service
public class GoogleHealthConnectionService {

  private static final Logger log = LoggerFactory.getLogger(GoogleHealthConnectionService.class);

  /** Refresh slightly before expiry to avoid racing the clock. */
  private static final long REFRESH_SKEW_SECONDS = 60;

  private final GoogleHealthConnectionRepository connectionRepository;
  private final UserRepository userRepository;
  private final GoogleHealthOAuthService oauthService;
  private final NotificationService notificationService;
  private final TokenCipher tokenCipher;
  private final GoogleHealthProperties properties;

  public GoogleHealthConnectionService(
      GoogleHealthConnectionRepository connectionRepository,
      UserRepository userRepository,
      GoogleHealthOAuthService oauthService,
      NotificationService notificationService,
      TokenCipher tokenCipher,
      GoogleHealthProperties properties) {
    this.connectionRepository = connectionRepository;
    this.userRepository = userRepository;
    this.oauthService = oauthService;
    this.notificationService = notificationService;
    this.tokenCipher = tokenCipher;
    this.properties = properties;
  }

  /**
   * Completes the OAuth callback: verifies {@code state}, exchanges the code, and stores (or
   * replaces) the connection for the user the state was issued to.
   *
   * @return the id of the user that was connected
   */
  @Transactional
  public java.util.UUID completeConnection(String code, String state) {
    java.util.UUID userId = oauthService.consumeStateToUserId(state);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    TokenResponse tokens = oauthService.exchangeCode(code);
    if (tokens.refreshToken() == null) {
      throw new IllegalStateException(
          "Google did not return a refresh token; ensure access_type=offline and prompt=consent");
    }

    GoogleHealthConnection connection =
        connectionRepository
            .findByUserId(userId)
            .orElseGet(() -> GoogleHealthConnection.builder().user(user).build());

    connection.setAccessToken(tokenCipher.encrypt(tokens.accessToken()));
    connection.setRefreshToken(tokenCipher.encrypt(tokens.refreshToken()));
    connection.setTokenExpiresAt(tokens.expiresAt());
    connection.setScopes(tokens.scopes());
    connection.setStatus(ConnectionStatus.CONNECTED);
    connectionRepository.save(connection);
    log.info("Google Health connection established for user {}", userId);
    return userId;
  }

  /** Removes the connection (the owner can re-link at any time). */
  @Transactional
  public void disconnect(java.util.UUID userId) {
    connectionRepository.findByUserId(userId).ifPresent(connectionRepository::delete);
    log.info("Google Health connection removed for user {}", userId);
  }

  @Transactional(readOnly = true)
  public Optional<GoogleHealthConnection> findConnection(java.util.UUID userId) {
    return connectionRepository.findByUserId(userId);
  }

  /**
   * Returns a usable access token for the connection, refreshing it first if it is at/near expiry.
   * The (possibly updated) tokens are persisted.
   *
   * @throws RefreshTokenRevokedException if re-consent is required; the connection has already been
   *     marked {@link ConnectionStatus#NEEDS_RECONNECT} and the owner notified
   */
  @Transactional
  public String getFreshAccessToken(GoogleHealthConnection connection) {
    if (!isExpiringSoon(connection)) {
      return tokenCipher.decrypt(connection.getAccessToken());
    }

    String refreshToken = tokenCipher.decrypt(connection.getRefreshToken());
    try {
      TokenResponse tokens = oauthService.refresh(refreshToken);
      connection.setAccessToken(tokenCipher.encrypt(tokens.accessToken()));
      if (tokens.refreshToken() != null) {
        connection.setRefreshToken(tokenCipher.encrypt(tokens.refreshToken()));
      }
      connection.setTokenExpiresAt(tokens.expiresAt());
      connectionRepository.save(connection);
      return tokens.accessToken();
    } catch (RefreshTokenRevokedException e) {
      markNeedsReconnect(connection);
      throw e;
    }
  }

  private void markNeedsReconnect(GoogleHealthConnection connection) {
    connection.setStatus(ConnectionStatus.NEEDS_RECONNECT);
    connectionRepository.save(connection);
    notificationService.sendReconnectReminder(connection.getUser(), "google-health");
    log.warn(
        "Google Health connection for user {} needs reconnect (refresh token revoked)",
        connection.getUser().getId());
  }

  private boolean isExpiringSoon(GoogleHealthConnection connection) {
    LocalDateTime threshold = LocalDateTime.now().plusSeconds(REFRESH_SKEW_SECONDS);
    return connection.getTokenExpiresAt() == null
        || connection.getTokenExpiresAt().isBefore(threshold);
  }

  public GoogleHealthProperties getProperties() {
    return properties;
  }
}
