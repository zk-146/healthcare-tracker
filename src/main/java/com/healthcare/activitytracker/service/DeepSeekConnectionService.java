package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.entity.DeepSeekConnection;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.repository.DeepSeekConnectionRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import com.healthcare.activitytracker.util.TokenCipher;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the lifecycle of a user's own DeepSeek API key: saving it (encrypted), reporting whether
 * one is on file, supplying the decrypted key to {@link DeepSeekClient}, and removing it.
 *
 * <p>Mirrors {@link GoogleHealthConnectionService}'s shape for the same reason it exists as its own
 * class: a per-user external-service credential is its own lifecycle, kept out of the general
 * {@link ProfileService}/biometric-profile surface.
 */
@Service
public class DeepSeekConnectionService {

  private static final Logger log = LoggerFactory.getLogger(DeepSeekConnectionService.class);

  private final DeepSeekConnectionRepository connectionRepository;
  private final UserRepository userRepository;
  private final TokenCipher tokenCipher;

  public DeepSeekConnectionService(
      DeepSeekConnectionRepository connectionRepository,
      UserRepository userRepository,
      TokenCipher tokenCipher) {
    this.connectionRepository = connectionRepository;
    this.userRepository = userRepository;
    this.tokenCipher = tokenCipher;
  }

  /**
   * Saves (or replaces) the user's API key, encrypted at rest. The key is trimmed first: a value
   * pasted with a trailing newline or leading/trailing spaces — common when copying from a
   * terminal or a {@code .env} file — is not blank, so an untrimmed value would otherwise be
   * stored verbatim and then fail every DeepSeek call with no diagnostic pointing back to the
   * whitespace.
   */
  @Transactional
  public void saveApiKey(UUID userId, String apiKey) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    DeepSeekConnection connection =
        connectionRepository
            .findByUserId(userId)
            .orElseGet(() -> DeepSeekConnection.builder().user(user).build());
    connection.setApiKeyEncrypted(tokenCipher.encrypt(apiKey.trim()));
    connectionRepository.save(connection);
    log.info("DeepSeek API key saved for user {}", userId);
  }

  /** Removes the user's key, if any. Idempotent — a no-op when nothing is on file. */
  @Transactional
  public void disconnect(UUID userId) {
    connectionRepository.findByUserId(userId).ifPresent(connectionRepository::delete);
    log.info("DeepSeek connection removed for user {}", userId);
  }

  @Transactional(readOnly = true)
  public boolean isConnected(UUID userId) {
    return connectionRepository.findByUserId(userId).isPresent();
  }

  /** Decrypts and returns the user's own DeepSeek key, if they have configured one. */
  @Transactional(readOnly = true)
  public Optional<String> findDecryptedApiKey(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }
    return connectionRepository
        .findByUserId(userId)
        .map(DeepSeekConnection::getApiKeyEncrypted)
        .map(tokenCipher::decrypt);
  }
}
