package com.healthcare.activitytracker.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Counts consecutive failed logins per account and locks the account out once they cross a
 * threshold.
 *
 * <p>{@code RateLimitingFilter} throttles by client IP, which does nothing against credential
 * stuffing spread across many source addresses: every IP arrives with a fresh budget. This counts
 * against the account being attacked instead, so the attempts add up no matter where they come
 * from.
 *
 * <p>Storage mirrors {@link TokenBlacklistService}: Redis when it is available, so a lockout holds
 * across every instance, dual-written to a local map so this instance still enforces what it saw.
 * Unlike revocation this deliberately <strong>fails open</strong> — if Redis is unreachable the
 * cross-instance count is lost rather than locking every account out of a degraded system, matching
 * the availability tradeoff already accepted for the blacklist. Redis failures log at ERROR.
 *
 * <p>Keys are the SHA-256 of the normalised email, not the address itself: the counter needs to
 * identify an account, not store who it belongs to, so there is no reason to put an email address
 * in Redis.
 */
@Component
public class LoginAttemptService {

  private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
  private static final String REDIS_KEY_PREFIX = "login:failures:";

  @Nullable private final StringRedisTemplate redisTemplate;

  private final int maxAttempts;
  private final Duration lockoutDuration;

  /** Fallback store: hashed email -> attempts so far, and when the window expires (epoch ms). */
  private final ConcurrentHashMap<String, LocalAttempts> localAttempts = new ConcurrentHashMap<>();

  private final LongSupplier nowMs;

  // With two declared constructors and neither a no-arg nor a Kotlin-style primary constructor,
  // Spring can't infer which one to wire without this: it would otherwise fail bean creation
  // with "No default constructor found".
  @Autowired
  public LoginAttemptService(
      @Nullable StringRedisTemplate redisTemplate,
      @Value("${app.security.login.max-attempts:10}") int maxAttempts,
      @Value("${app.security.login.lockout-minutes:15}") int lockoutMinutes) {
    this(redisTemplate, maxAttempts, lockoutMinutes, System::currentTimeMillis);
  }

  /** Seam for tests that need to cross the lockout window without sleeping. */
  LoginAttemptService(
      @Nullable StringRedisTemplate redisTemplate,
      int maxAttempts,
      int lockoutMinutes,
      LongSupplier nowMs) {
    this.redisTemplate = redisTemplate;
    this.nowMs = nowMs;
    this.maxAttempts = maxAttempts;
    this.lockoutDuration = Duration.ofMinutes(lockoutMinutes);
    if (redisTemplate != null) {
      log.info(
          "Login attempt limiting using Redis (distributed): {} attempts per {} minutes",
          maxAttempts,
          lockoutMinutes);
    } else {
      log.warn(
          "Login attempt limiting using in-memory store (single-instance only): "
              + "{} attempts per {} minutes",
          maxAttempts,
          lockoutMinutes);
    }
  }

  /** Records one failed authentication attempt against the account. */
  public void recordFailure(String email) {
    String key = hashEmail(email);
    long now = nowMs.getAsLong();

    localAttempts.compute(
        key,
        (k, existing) ->
            existing == null || now > existing.windowExpiresAtMs()
                ? new LocalAttempts(1, now + lockoutDuration.toMillis())
                : new LocalAttempts(existing.count() + 1, existing.windowExpiresAtMs()));

    if (redisTemplate != null) {
      try {
        Long count = redisTemplate.opsForValue().increment(REDIS_KEY_PREFIX + key);
        // Set the TTL on the first failure so the window runs from that attempt, not the last.
        if (count != null && count == 1L) {
          redisTemplate.expire(REDIS_KEY_PREFIX + key, lockoutDuration);
        }
      } catch (Exception e) {
        log.error(
            "Redis unavailable for login attempt tracking — failures are only counted on this "
                + "instance until Redis recovers: {}",
            e.getMessage());
      }
    }
  }

  /** Returns true when the account has exhausted its attempts and is inside the lockout window. */
  public boolean isLockedOut(String email) {
    String key = hashEmail(email);

    LocalAttempts local = localAttempts.get(key);
    if (local != null
        && nowMs.getAsLong() <= local.windowExpiresAtMs()
        && local.count() >= maxAttempts) {
      return true;
    }

    if (redisTemplate != null) {
      try {
        String value = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + key);
        return value != null && Long.parseLong(value) >= maxAttempts;
      } catch (Exception e) {
        // Fail open: a Redis outage must not lock every account out of the application.
        log.error(
            "Redis unavailable for login lockout check — failures from other instances cannot "
                + "be seen until Redis recovers: {}",
            e.getMessage());
      }
    }
    return false;
  }

  /** Clears the counter after a successful authentication. */
  public void clearFailures(String email) {
    String key = hashEmail(email);
    localAttempts.remove(key);
    if (redisTemplate != null) {
      try {
        redisTemplate.delete(REDIS_KEY_PREFIX + key);
      } catch (Exception e) {
        log.error("Redis unavailable to clear login failures: {}", e.getMessage());
      }
    }
  }

  private String hashEmail(String email) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash =
          digest.digest(email.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /** Purges elapsed windows from the in-memory fallback; Redis entries expire via TTL. */
  @Scheduled(fixedRate = 300_000)
  public void evictElapsedWindows() {
    long now = nowMs.getAsLong();
    int before = localAttempts.size();
    localAttempts.entrySet().removeIf(entry -> now > entry.getValue().windowExpiresAtMs());
    int removed = before - localAttempts.size();
    if (removed > 0) {
      log.debug(
          "Login attempt eviction: removed {} elapsed windows, remaining={}",
          removed,
          localAttempts.size());
    }
  }

  /** Consecutive failures inside one lockout window. */
  private record LocalAttempts(int count, long windowExpiresAtMs) {}
}
