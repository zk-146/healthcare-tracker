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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and validates email-verification tokens. Verification is tracked but not enforced at login
 * — {@link User#isEmailVerified()} simply reflects whether the user has confirmed their address.
 */
@Service
public class EmailVerificationService {

  private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

  private final OneTimeTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final EmailService emailService;
  private final AuditService auditService;
  private final long expiryMs;

  public EmailVerificationService(
      OneTimeTokenRepository tokenRepository,
      UserRepository userRepository,
      EmailService emailService,
      AuditService auditService,
      @Value("${app.tokens.email-verification-expiry-ms:86400000}") long expiryMs) {
    this.tokenRepository = tokenRepository;
    this.userRepository = userRepository;
    this.emailService = emailService;
    this.auditService = auditService;
    this.expiryMs = expiryMs;
  }

  /** Issues a fresh verification token for the user and dispatches the verification email. */
  @Transactional
  public void issueFor(User user) {
    tokenRepository.invalidateActiveTokens(user.getId(), TokenPurpose.EMAIL_VERIFICATION);

    String rawToken = TokenHasher.generateRawToken();
    OneTimeToken token =
        OneTimeToken.builder()
            .user(user)
            .tokenHash(TokenHasher.sha256(rawToken))
            .purpose(TokenPurpose.EMAIL_VERIFICATION)
            .used(false)
            .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plus(Duration.ofMillis(expiryMs)))
            .build();
    tokenRepository.save(token);

    emailService.sendVerificationEmail(user, rawToken);
  }

  /**
   * Marks the user's email as verified given a valid, unused, unexpired token.
   *
   * @throws UnauthorizedException if the token is unknown, already used, or expired
   */
  @Transactional
  public void verify(String rawToken) {
    OneTimeToken token =
        tokenRepository
            .findByTokenHashAndPurpose(
                TokenHasher.sha256(rawToken), TokenPurpose.EMAIL_VERIFICATION)
            .orElseThrow(() -> new UnauthorizedException("Invalid or expired verification token"));

    if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
      throw new UnauthorizedException("Invalid or expired verification token");
    }

    token.setUsed(true);
    tokenRepository.save(token);

    User user = token.getUser();
    user.setEmailVerified(true);
    userRepository.save(user);

    auditService.record(user.getId(), AuditEventType.EMAIL_VERIFIED);
    log.info("Email verified for userId={}", user.getId());
  }

  /**
   * Re-issues a verification token for the given email if the account exists and is not yet
   * verified. Always returns silently to avoid revealing whether an email is registered.
   */
  @Transactional
  public void resend(String email) {
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    userRepository
        .findByEmail(normalized)
        .filter(user -> !user.isEmailVerified())
        .ifPresent(this::issueFor);
  }

  /** Periodically purge expired one-time tokens (verification and reset). */
  @Scheduled(fixedRate = 3_600_000)
  @Transactional
  public void cleanupExpiredTokens() {
    int deleted = tokenRepository.deleteExpired(LocalDateTime.now(ZoneOffset.UTC));
    if (deleted > 0) {
      log.info("Cleaned up {} expired one-time tokens", deleted);
    }
  }
}
