package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.config.MailProperties;
import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.entity.AccountToken;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.AccountTokenPurpose;
import com.healthcare.activitytracker.repository.AccountTokenRepository;
import com.healthcare.activitytracker.repository.RefreshTokenRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset and email verification: the two things a user does by following an emailed link.
 *
 * <p>Both work the same way. A random 256-bit token is emailed inside a link and only its SHA-256
 * is stored. Following the link redeems the token with one atomic conditional UPDATE, so it works
 * once, only for the purpose it was issued for, and only until it expires.
 *
 * <p>Links put the token in the URL <em>fragment</em> ({@code /#reset-password=...}). A fragment is
 * never sent to the server, so the token stays out of request logs, proxy logs and Referer headers;
 * the single-page app reads it and submits it in a POST body.
 */
@Service
public class AccountRecoveryService {

  private static final Logger log = LoggerFactory.getLogger(AccountRecoveryService.class);

  /** One message for every way a link can be bad, so the response says nothing about which. */
  static final String INVALID_LINK =
      "This link is invalid or has expired. Please request a new one.";

  private static final int TOKEN_BYTES = 32;

  private final UserRepository userRepository;
  private final AccountTokenRepository tokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttemptService;
  private final EmailSender emailSender;
  private final MailProperties mailProperties;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  @Autowired
  public AccountRecoveryService(
      UserRepository userRepository,
      AccountTokenRepository tokenRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      LoginAttemptService loginAttemptService,
      EmailSender emailSender,
      MailProperties mailProperties) {
    this(
        userRepository,
        tokenRepository,
        refreshTokenRepository,
        passwordEncoder,
        loginAttemptService,
        emailSender,
        mailProperties,
        Clock.systemUTC());
  }

  /** Seam for tests that need a fixed "now". */
  AccountRecoveryService(
      UserRepository userRepository,
      AccountTokenRepository tokenRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      LoginAttemptService loginAttemptService,
      EmailSender emailSender,
      MailProperties mailProperties,
      Clock clock) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.loginAttemptService = loginAttemptService;
    this.emailSender = emailSender;
    this.mailProperties = mailProperties;
    this.clock = clock;
  }

  /**
   * Emails a reset link if the address belongs to an account, and does nothing if it does not.
   *
   * <p>There is no return value and no exception either way, on purpose: the caller is anonymous,
   * and any observable difference would turn this endpoint into a way to test which addresses are
   * registered.
   */
  @Transactional
  public void requestPasswordReset(String email) {
    String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
    userRepository
        .findByEmail(normalizedEmail)
        .ifPresent(
            user -> {
              String token = issue(user, AccountTokenPurpose.PASSWORD_RESET);
              if (token != null) {
                emailSender.send(
                    user.getEmail(), "Reset your Activity Tracker password", resetBody(token));
                log.info("Password reset requested for user {}", user.getId());
              }
            });
  }

  /**
   * Sets a new password using a token from a reset email.
   *
   * <p>On success every refresh token is revoked, so all other sessions must sign in again with the
   * new password (access tokens already issued run out within their 15 minutes, as with a normal
   * password change). The failed-login counter is cleared — a lockout is what often sends someone
   * here — and the address is marked verified, since receiving the link proves control of it.
   *
   * @throws IllegalArgumentException if the token is unknown, already used, expired, or was issued
   *     for something else
   */
  @Transactional
  public void resetPassword(String rawToken, String newPassword) {
    User user = redeem(rawToken, AccountTokenPurpose.PASSWORD_RESET);

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setEmailVerified(true);
    userRepository.save(user);

    refreshTokenRepository.revokeAllByUserId(user.getId());
    // Any other reset link still in the inbox dies with this one.
    tokenRepository.deleteAllByUserIdAndPurpose(user.getId(), AccountTokenPurpose.PASSWORD_RESET);
    loginAttemptService.clearFailures(user.getEmail());

    emailSender.send(
        user.getEmail(),
        "Your Activity Tracker password was changed",
        "The password for your Activity Tracker account was just changed using a reset link.\n\n"
            + "If this was you, there is nothing more to do.\n\n"
            + "If it was not, someone else has access to your email. Reset your password again "
            + "at "
            + baseUrl()
            + " and secure your email account.\n");
    log.info("Password reset completed for user {}", user.getId());
  }

  /** Emails a verification link to a newly registered user. A no-op once verified. */
  @Transactional
  public void sendVerificationEmail(User user) {
    if (user.isEmailVerified()) {
      return;
    }
    String token = issue(user, AccountTokenPurpose.EMAIL_VERIFICATION);
    if (token != null) {
      emailSender.send(
          user.getEmail(), "Confirm your Activity Tracker email address", verificationBody(token));
    }
  }

  /**
   * Sends the signed-in user a fresh verification link.
   *
   * @throws ResourceNotFoundException if the user does not exist
   */
  @Transactional
  public void resendVerification(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    sendVerificationEmail(user);
  }

  /**
   * Marks the token's account as verified.
   *
   * @throws IllegalArgumentException if the token is unknown, already used, expired, or was issued
   *     for something else
   */
  @Transactional
  public void verifyEmail(String rawToken) {
    User user = redeem(rawToken, AccountTokenPurpose.EMAIL_VERIFICATION);
    user.setEmailVerified(true);
    userRepository.save(user);
    tokenRepository.deleteAllByUserIdAndPurpose(
        user.getId(), AccountTokenPurpose.EMAIL_VERIFICATION);
    log.info("Email verified for user {}", user.getId());
  }

  /** Removes expired tokens. Runs every hour. */
  @Scheduled(fixedRate = 3_600_000)
  @Transactional
  public void cleanupExpiredTokens() {
    int deleted = tokenRepository.deleteExpired(now());
    if (deleted > 0) {
      log.info("Cleaned up {} expired account tokens", deleted);
    }
  }

  /**
   * Stores a new token for the user and returns its raw value, or null if one of the same kind was
   * issued within the cooldown — in which case nothing is sent, quietly.
   */
  private String issue(User user, AccountTokenPurpose purpose) {
    LocalDateTime now = now();
    LocalDateTime cooldownStart = now.minusSeconds(mailProperties.getResendCooldownSeconds());
    if (tokenRepository.existsIssuedSince(user.getId(), purpose, cooldownStart)) {
      log.info("{} email suppressed for user {}: one was sent recently", purpose, user.getId());
      return null;
    }

    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    tokenRepository.save(
        AccountToken.builder()
            .user(user)
            .purpose(purpose)
            .tokenHash(AuthService.sha256(rawToken))
            .createdAt(now)
            .expiresAt(now.plus(lifetime(purpose)))
            .build());
    return rawToken;
  }

  /** Claims the token and returns its user, or throws {@link #INVALID_LINK}. */
  private User redeem(String rawToken, AccountTokenPurpose purpose) {
    String tokenHash = AuthService.sha256(rawToken);
    if (tokenRepository.claim(tokenHash, purpose, now()) == 0) {
      throw new IllegalArgumentException(INVALID_LINK);
    }
    return tokenRepository
        .findByTokenHash(tokenHash)
        .map(AccountToken::getUser)
        .orElseThrow(() -> new IllegalArgumentException(INVALID_LINK));
  }

  private Duration lifetime(AccountTokenPurpose purpose) {
    return purpose == AccountTokenPurpose.PASSWORD_RESET
        ? Duration.ofMinutes(mailProperties.getResetTokenMinutes())
        : Duration.ofHours(mailProperties.getVerificationTokenHours());
  }

  private String resetBody(String token) {
    return "Someone asked to reset the password for your Activity Tracker account.\n\n"
        + "To choose a new password, open this link within "
        + mailProperties.getResetTokenMinutes()
        + " minutes:\n\n"
        + baseUrl()
        + "/#reset-password="
        + token
        + "\n\nThe link works once. If you did not ask for this, ignore this email — your "
        + "password has not been changed.\n";
  }

  private String verificationBody(String token) {
    return "Welcome to Activity Tracker.\n\n"
        + "Confirm that this is your email address by opening this link within "
        + mailProperties.getVerificationTokenHours()
        + " hours:\n\n"
        + baseUrl()
        + "/#verify-email="
        + token
        + "\n\nIf you did not create an account, ignore this email.\n";
  }

  private String baseUrl() {
    String url = mailProperties.getPublicBaseUrl().strip();
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
