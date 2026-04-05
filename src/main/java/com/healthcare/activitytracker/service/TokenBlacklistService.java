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

  @Nullable private final StringRedisTemplate redisTemplate;

  // Fallback in-memory store: token -> absolute expiry time in epoch ms
  private final ConcurrentHashMap<String, Long> localBlacklist = new ConcurrentHashMap<>();

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
    int removed = before - localBlacklist.size();
    if (removed > 0) {
      log.debug(
          "Token blacklist eviction: removed {} expired entries, remaining={}",
          removed,
          localBlacklist.size());
    }
  }
}
