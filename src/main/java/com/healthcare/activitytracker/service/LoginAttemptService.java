package com.healthcare.activitytracker.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tracks consecutive failed login attempts per account and temporarily locks an account once a
 * threshold is exceeded. This complements the per-IP rate limiter by defending a single account
 * against distributed (multi-IP) password brute-force attempts.
 *
 * <p>Uses Redis when available so the counter is shared across instances; otherwise falls back to a
 * local in-memory map (single-instance only).
 */
@Component
public class LoginAttemptService {

  private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
  private static final String REDIS_KEY_PREFIX = "login:attempts:";

  @Value("${app.security.login.max-failed-attempts:5}")
  private int maxFailedAttempts;

  @Value("${app.security.login.lock-duration-minutes:15}")
  private long lockDurationMinutes;

  @Nullable private final StringRedisTemplate redisTemplate;

  // Fallback in-memory store: account key -> {failureCount, windowExpiresAtMs}
  private final ConcurrentHashMap<String, long[]> localAttempts = new ConcurrentHashMap<>();

  public LoginAttemptService(@Nullable StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /** Returns true if the account is currently locked due to too many failed attempts. */
  public boolean isLocked(String email) {
    String key = accountKey(email);
    long count;
    if (redisTemplate != null) {
      try {
        String value = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + key);
        count = value == null ? 0 : Long.parseLong(value);
        return count >= maxFailedAttempts;
      } catch (Exception e) {
        log.warn(
            "Redis unavailable for lockout check, falling back to in-memory: {}", e.getMessage());
      }
    }
    long[] entry = localAttempts.get(key);
    if (entry == null) {
      return false;
    }
    if (System.currentTimeMillis() > entry[1]) {
      localAttempts.remove(key);
      return false;
    }
    return entry[0] >= maxFailedAttempts;
  }

  /** Records a failed login attempt, starting (or extending) the lockout window. */
  public void recordFailure(String email) {
    String key = accountKey(email);
    if (redisTemplate != null) {
      try {
        Long count = redisTemplate.opsForValue().increment(REDIS_KEY_PREFIX + key);
        if (count != null && count == 1L) {
          redisTemplate.expire(REDIS_KEY_PREFIX + key, Duration.ofMinutes(lockDurationMinutes));
        }
        return;
      } catch (Exception e) {
        log.warn(
            "Redis unavailable for recording login failure, falling back to in-memory: {}",
            e.getMessage());
      }
    }
    long windowMs = Duration.ofMinutes(lockDurationMinutes).toMillis();
    localAttempts.compute(
        key,
        (k, existing) -> {
          long now = System.currentTimeMillis();
          if (existing == null || now > existing[1]) {
            return new long[] {1, now + windowMs};
          }
          return new long[] {existing[0] + 1, existing[1]};
        });
  }

  /** Clears the failure counter for an account after a successful login. */
  public void reset(String email) {
    String key = accountKey(email);
    if (redisTemplate != null) {
      try {
        redisTemplate.delete(REDIS_KEY_PREFIX + key);
        return;
      } catch (Exception e) {
        log.warn(
            "Redis unavailable for resetting login attempts, falling back to in-memory: {}",
            e.getMessage());
      }
    }
    localAttempts.remove(key);
  }

  /** Purge expired windows from the in-memory fallback. Redis entries auto-expire via TTL. */
  @Scheduled(fixedRate = 300_000)
  public void evictExpired() {
    long now = System.currentTimeMillis();
    localAttempts.entrySet().removeIf(entry -> now > entry.getValue()[1]);
  }

  /**
   * Hashes the account identifier so raw emails are never used as cache keys. Keeps keys uniform in
   * length and avoids storing PII in the rate-limit store.
   */
  private String accountKey(String email) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(email.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
