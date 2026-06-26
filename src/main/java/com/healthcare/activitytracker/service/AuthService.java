package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.ResourceConflictException;
import com.healthcare.activitytracker.exception.UnauthorizedException;
import com.healthcare.activitytracker.model.dto.AuthResponse;
import com.healthcare.activitytracker.model.dto.LoginRequest;
import com.healthcare.activitytracker.model.dto.RegisterRequest;
import com.healthcare.activitytracker.model.entity.RefreshToken;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.AuditEventType;
import com.healthcare.activitytracker.repository.RefreshTokenRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import com.healthcare.activitytracker.util.JwtUtil;
import com.healthcare.activitytracker.util.TokenHasher;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final TokenBlacklistService tokenBlacklistService;
  private final EmailVerificationService emailVerificationService;
  private final AuditService auditService;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtUtil jwtUtil,
      TokenBlacklistService tokenBlacklistService,
      EmailVerificationService emailVerificationService,
      AuditService auditService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
    this.tokenBlacklistService = tokenBlacklistService;
    this.emailVerificationService = emailVerificationService;
    this.auditService = auditService;
  }

  /**
   * Registers a new user account. The email is normalized (stripped and lowercased) before
   * persistence.
   *
   * @param request the registration details (email, password, full name)
   * @return access and refresh tokens for the newly created user
   * @throws com.healthcare.activitytracker.exception.ResourceConflictException if the email is
   *     already registered
   */
  @Transactional
  public AuthResponse register(RegisterRequest request) {
    // Normalize email to prevent duplicate accounts differing only by case/whitespace
    String normalizedEmail = request.getEmail().strip().toLowerCase(Locale.ROOT);

    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new ResourceConflictException("Email already registered");
    }

    User user =
        User.builder()
            .email(normalizedEmail)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName())
            .build();

    user = userRepository.save(user);
    log.info("New user registered: {}", user.getId());

    // Kick off email-verification (tracked, not enforced) and record the event.
    emailVerificationService.issueFor(user);
    auditService.record(user.getId(), AuditEventType.REGISTER);

    return buildAuthResponse(user);
  }

  /**
   * Authenticates a user by email and password.
   *
   * @param request the login credentials
   * @return access and refresh tokens on successful authentication
   * @throws com.healthcare.activitytracker.exception.UnauthorizedException if the email is not
   *     found or the password does not match
   */
  @Transactional
  public AuthResponse login(LoginRequest request) {
    String normalizedEmail = request.getEmail().strip().toLowerCase(Locale.ROOT);

    User user =
        userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(
                () -> {
                  auditService.record(null, AuditEventType.LOGIN_FAILURE, "reason=unknown-email");
                  return new UnauthorizedException("Invalid email or password");
                });

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      log.warn("Failed login attempt for userId: {}", user.getId());
      auditService.record(user.getId(), AuditEventType.LOGIN_FAILURE, "reason=bad-password");
      throw new UnauthorizedException("Invalid email or password");
    }

    log.info("User logged in: {}", user.getId());
    auditService.record(user.getId(), AuditEventType.LOGIN_SUCCESS);
    return buildAuthResponse(user);
  }

  /**
   * Issues a new access token in exchange for a valid, non-revoked refresh token. The supplied
   * refresh token is revoked after use (rotation), and a new refresh token is issued alongside the
   * new access token.
   *
   * @param refreshToken the raw refresh token JWT
   * @return new access and refresh tokens
   * @throws com.healthcare.activitytracker.exception.UnauthorizedException if the token is invalid,
   *     expired, the wrong type, not found in the database, or already revoked
   */
  @Transactional
  public AuthResponse refresh(String refreshToken) {
    if (!jwtUtil.isRefreshTokenValid(refreshToken)) {
      throw new UnauthorizedException("Invalid or expired refresh token");
    }
    // Token type confusion is now also prevented cryptographically (separate keys),
    // but we still verify the claim for defence-in-depth.
    Claims claims = jwtUtil.parseRefreshToken(refreshToken);
    if (!JwtUtil.TOKEN_TYPE_REFRESH.equals(claims.get("type", String.class))) {
      throw new UnauthorizedException("Invalid token type");
    }

    // Verify the refresh token exists in the DB and is not revoked
    String tokenHash = sha256(refreshToken);
    RefreshToken storedToken =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));
    if (storedToken.isRevoked()) {
      throw new UnauthorizedException("Refresh token has been revoked");
    }

    var userId = jwtUtil.getUserIdFromRefreshToken(refreshToken);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

    // Revoke the used refresh token so it cannot be replayed
    storedToken.setRevoked(true);
    refreshTokenRepository.save(storedToken);

    // Also add to the fast-path blacklist (Redis/in-memory) for immediate enforcement
    long remainingMs = claims.getExpiration().getTime() - new Date().getTime();
    tokenBlacklistService.revoke(refreshToken, Math.max(remainingMs, 0));

    return buildAuthResponse(user);
  }

  /**
   * Revokes the provided access token, effectively logging the user out. Also revokes all active
   * refresh tokens for the user. No-op if the token is already invalid or expired.
   */
  @Transactional
  public void logout(String accessToken) {
    try {
      if (jwtUtil.isAccessTokenValid(accessToken)) {
        Claims claims = jwtUtil.parseAccessToken(accessToken);
        long remainingMs = claims.getExpiration().getTime() - new Date().getTime();
        tokenBlacklistService.revoke(accessToken, Math.max(remainingMs, 0));

        // Revoke all refresh tokens for this user
        var userId = jwtUtil.getUserIdFromToken(accessToken);
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        log.info(
            "User {} logged out: access token blacklisted, {} refresh token(s) revoked",
            userId,
            revoked);
        auditService.record(userId, AuditEventType.LOGOUT);
      }
    } catch (Exception e) {
      // Token is already invalid — nothing to revoke
      log.debug("Logout called with already-invalid token: {}", e.getMessage());
    }
  }

  /** Revoke all refresh tokens for a user (e.g. on password change or security incident). */
  @Transactional
  public void revokeAllUserTokens(java.util.UUID userId) {
    int revoked = refreshTokenRepository.revokeAllByUserId(userId);
    log.info("Revoked {} refresh token(s) for user {}", revoked, userId);
  }

  /** Clean up expired refresh tokens from the database. Runs every hour. */
  @Scheduled(fixedRate = 3_600_000)
  @Transactional
  public void cleanupExpiredRefreshTokens() {
    int deleted = refreshTokenRepository.deleteExpired(LocalDateTime.now(ZoneOffset.UTC));
    if (deleted > 0) {
      log.info("Cleaned up {} expired refresh tokens", deleted);
    }
  }

  private AuthResponse buildAuthResponse(User user) {
    String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
    String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

    // Persist the refresh token hash in the database
    Claims refreshClaims = jwtUtil.parseRefreshToken(refreshToken);
    LocalDateTime expiresAt =
        Instant.ofEpochMilli(refreshClaims.getExpiration().getTime())
            .atZone(ZoneOffset.UTC)
            .toLocalDateTime();

    RefreshToken storedToken =
        RefreshToken.builder()
            .tokenHash(sha256(refreshToken))
            .user(user)
            .revoked(false)
            .expiresAt(expiresAt)
            .build();
    refreshTokenRepository.save(storedToken);

    return AuthResponse.builder()
        .token(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000)
        .userId(user.getId().toString())
        .email(user.getEmail())
        .build();
  }

  /**
   * SHA-256 hash of the raw token. We store the hash in the DB rather than the raw JWT to limit
   * exposure if the database is compromised. Delegates to {@link TokenHasher} (kept as a static
   * method for existing callers/tests).
   */
  static String sha256(String input) {
    return TokenHasher.sha256(input);
  }
}
