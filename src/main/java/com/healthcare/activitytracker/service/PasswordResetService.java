package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.UnauthorizedException;
import com.healthcare.activitytracker.model.entity.OneTimeToken;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.AuditEventType;
import com.healthcare.activitytracker.model.enums.TokenPurpose;
import com.healthcare.activitytracker.repository.OneTimeTokenRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import com.healthcare.activitytracker.util.TokenHasher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the forgot-password / reset-password flow. Both entry points avoid user enumeration: a
 * request for an unknown email succeeds silently, and only valid tokens reveal anything.
 */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

  private final OneTimeTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final EmailService emailService;
  private final PasswordEncoder passwordEncoder;
  private final AuthService authService;
  private final AuditService auditService;
  private final long expiryMs;

  public PasswordResetService(
      OneTimeTokenRepository tokenRepository,
      UserRepository userRepository,
      EmailService emailService,
      PasswordEncoder passwordEncoder,
      AuthService authService,
      AuditService auditService,
      @Value("${app.tokens.password-reset-expiry-ms:3600000}") long expiryMs) {
    this.tokenRepository = tokenRepository;
    this.userRepository = userRepository;
    this.emailService = emailService;
    this.passwordEncoder = passwordEncoder;
    this.authService = authService;
    this.auditService = auditService;
    this.expiryMs = expiryMs;
  }

  /**
   * Issues a password-reset token and emails it, if the account exists. Always returns silently to
   * avoid revealing whether an email is registered.
   */
  @Transactional
  public void requestReset(String email) {
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    userRepository
        .findByEmail(normalized)
        .ifPresent(
            user -> {
              tokenRepository.invalidateActiveTokens(user.getId(), TokenPurpose.PASSWORD_RESET);

              String rawToken = TokenHasher.generateRawToken();
              OneTimeToken token =
                  OneTimeToken.builder()
                      .user(user)
                      .tokenHash(TokenHasher.sha256(rawToken))
                      .purpose(TokenPurpose.PASSWORD_RESET)
                      .used(false)
                      .expiresAt(
                          LocalDateTime.now(ZoneOffset.UTC).plus(Duration.ofMillis(expiryMs)))
                      .build();
              tokenRepository.save(token);

              emailService.sendPasswordResetEmail(user, rawToken);
              auditService.record(user.getId(), AuditEventType.PASSWORD_RESET_REQUEST);
            });
  }

  /**
   * Resets the password given a valid, unused, unexpired token. Revokes all of the user's refresh
   * tokens so existing sessions cannot continue with the old credentials.
   *
   * @throws UnauthorizedException if the token is unknown, already used, or expired
   */
  @Transactional
  public void reset(String rawToken, String newPassword) {
    OneTimeToken token =
        tokenRepository
            .findByTokenHashAndPurpose(TokenHasher.sha256(rawToken), TokenPurpose.PASSWORD_RESET)
            .orElseThrow(() -> new UnauthorizedException("Invalid or expired reset token"));

    if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
      throw new UnauthorizedException("Invalid or expired reset token");
    }

    token.setUsed(true);
    tokenRepository.save(token);

    User user = token.getUser();
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    authService.revokeAllUserTokens(user.getId());
    auditService.record(user.getId(), AuditEventType.PASSWORD_RESET);
    log.info("Password reset completed for userId={}", user.getId());
  }
}
