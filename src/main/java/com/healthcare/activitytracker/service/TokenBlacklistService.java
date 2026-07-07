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
 * Every revocation is <em>also</em> written to a local in-memory {@link ConcurrentHashMap}, so a
 * Redis outage can never resurrect a token that was revoked through this instance. Revocations
 * recorded by <em>other</em> instances are unavoidably invisible while Redis is down (fail-open for
 * cross-instance checks only); such failures are logged at ERROR level so they page.
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
    // Always record locally first — this instance must never un-revoke a token it revoked,
    // even if the Redis write below fails.
    localBlacklist.put(key, System.currentTimeMillis() + expiryMs);
    if (redisTemplate != null) {
      try {
        redisTemplate
            .opsForValue()
            .set(REDIS_KEY_PREFIX + key, "revoked", Duration.ofMillis(expiryMs));
        log.debug("Token revoked in Redis and locally");
      } catch (Exception e) {
        log.error(
            "Redis unavailable for token revocation — revocation is only effective on this "
                + "instance until Redis recovers: {}",
            e.getMessage());
      }
    }
  }

  /** Returns true if the token has been explicitly revoked. */
  public boolean isRevoked(String token) {
    String key = hashToken(token);
    // Local check first: authoritative for revocations made through this instance and
    // resilient to Redis outages.
    if (isLocallyRevoked(key)) {
      return true;
    }
    if (redisTemplate != null) {
      try {
        Boolean exists = redisTemplate.hasKey(REDIS_KEY_PREFIX + key);
        return Boolean.TRUE.equals(exists);
      } catch (Exception e) {
        log.error(
            "Redis unavailable for revocation check — revocations from other instances "
                + "cannot be seen until Redis recovers: {}",
            e.getMessage());
      }
    }
    return false;
  }

  private boolean isLocallyRevoked(String key) {
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
