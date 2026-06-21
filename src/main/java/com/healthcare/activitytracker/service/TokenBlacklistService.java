package com.healthcare.activitytracker.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Token blacklist for revoked JWTs.
 *
 * <p>Uses Redis when available so that revocations are visible across all application instances.
 * Falls back to a local in-memory {@link ConcurrentHashMap} if Redis is not configured (e.g. in
 * local development or tests).
 */
@Component
public class TokenBlacklistService {

  private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
  private static final String REDIS_KEY_PREFIX = "token:blacklist:";
  private static final String USER_INVALIDATION_PREFIX = "user:invalidate:";

  @Nullable private final StringRedisTemplate redisTemplate;

  // Fallback in-memory store: token -> absolute expiry time in epoch ms
  private final ConcurrentHashMap<String, Long> localBlacklist = new ConcurrentHashMap<>();

  // Fallback in-memory store for per-user invalidation cutoffs: userId -> {cutoffMs, expiresAtMs}
  private final ConcurrentHashMap<String, long[]> localUserInvalidation = new ConcurrentHashMap<>();

  public TokenBlacklistService(@Nullable StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
    if (redisTemplate != null) {
      log.info("Token blacklist using Redis (distributed)");
    } else {
      log.warn("Token blacklist using in-memory store (single-instance only)");
    }
  }

  /**
   * Add a token to the blacklist until it would naturally expire.
   *
   * @param token the raw JWT string
   * @param expiryMs remaining lifetime of the token in milliseconds
   */
  public void revoke(String token, long expiryMs) {
    String key = hashToken(token);
    if (redisTemplate != null) {
      try {
        redisTemplate
            .opsForValue()
            .set(REDIS_KEY_PREFIX + key, "revoked", Duration.ofMillis(expiryMs));
        log.debug("Token revoked in Redis");
        return;
      } catch (Exception e) {
        log.warn(
            "Redis unavailable for token revocation, falling back to in-memory: {}",
            e.getMessage());
      }
    }
    // Fallback to in-memory
    localBlacklist.put(key, System.currentTimeMillis() + expiryMs);
    log.debug("Token revoked in-memory, blacklist size={}", localBlacklist.size());
  }

  /** Returns true if the token has been explicitly revoked. */
  public boolean isRevoked(String token) {
    String key = hashToken(token);
    if (redisTemplate != null) {
      try {
        Boolean exists = redisTemplate.hasKey(REDIS_KEY_PREFIX + key);
        return Boolean.TRUE.equals(exists);
      } catch (Exception e) {
        log.warn(
            "Redis unavailable for revocation check, falling back to in-memory: {}",
            e.getMessage());
      }
    }
    // Fallback to in-memory
    Long expiresAt = localBlacklist.get(key);
    if (expiresAt == null) {
      return false;
    }
    if (System.currentTimeMillis() > expiresAt) {
      localBlacklist.remove(key);
      return false;
    }
    return true;
  }

  /**
   * Invalidates every access token issued for the given user up to the current instant. Used on
   * logout to enforce a "log out everywhere" semantic without enumerating individual tokens — any
   * access token whose {@code iat} predates this call is rejected by {@link
   * #isUserInvalidatedSince(String, long)}.
   *
   * @param userId the user whose existing access tokens should be invalidated
   * @param ttlMs how long the cutoff must be retained — set to the maximum access-token lifetime,
   *     so that once all pre-cutoff tokens have expired the marker can be discarded
   */
  public void revokeAllForUser(String userId, long ttlMs) {
    long now = System.currentTimeMillis();
    if (redisTemplate != null) {
      try {
        redisTemplate
            .opsForValue()
            .set(USER_INVALIDATION_PREFIX + userId, Long.toString(now), Duration.ofMillis(ttlMs));
        return;
      } catch (Exception e) {
        log.warn(
            "Redis unavailable for user invalidation, falling back to in-memory: {}",
            e.getMessage());
      }
    }
    localUserInvalidation.put(userId, new long[] {now, now + ttlMs});
  }

  /**
   * Returns true if the user has a logout/invalidation cutoff that is more recent than the supplied
   * token issue time, meaning the token was issued before the user logged out everywhere.
   *
   * @param userId the token subject
   * @param issuedAtMs the token's {@code iat} claim in epoch milliseconds
   */
  public boolean isUserInvalidatedSince(String userId, long issuedAtMs) {
    if (redisTemplate != null) {
      try {
        String cutoff = redisTemplate.opsForValue().get(USER_INVALIDATION_PREFIX + userId);
        return cutoff != null && issuedAtMs < Long.parseLong(cutoff);
      } catch (Exception e) {
        log.warn(
            "Redis unavailable for user invalidation check, falling back to in-memory: {}",
            e.getMessage());
      }
    }
    long[] entry = localUserInvalidation.get(userId);
    if (entry == null) {
      return false;
    }
    if (System.currentTimeMillis() > entry[1]) {
      localUserInvalidation.remove(userId);
      return false;
    }
    return issuedAtMs < entry[0];
  }

  /**
   * Returns the SHA-256 hex digest of the given token. Raw JWTs can be 500+ bytes; using a 64-char
   * hash keeps Redis keys small and avoids storing any recoverable token data in the blacklist
   * store.
   */
  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Periodically purge expired entries from the in-memory fallback map. Redis entries auto-expire
   * via TTL and don't need manual cleanup. Runs every 5 minutes.
   */
  @Scheduled(fixedRate = 300_000)
  public void evictExpiredTokens() {
    long now = System.currentTimeMillis();
    int before = localBlacklist.size();
    localBlacklist.entrySet().removeIf(entry -> now > entry.getValue());
    localUserInvalidation.entrySet().removeIf(entry -> now > entry.getValue()[1]);
    int removed = before - localBlacklist.size();
    if (removed > 0) {
      log.debug(
          "Token blacklist eviction: removed {} expired entries, remaining={}",
          removed,
          localBlacklist.size());
    }
  }
}
