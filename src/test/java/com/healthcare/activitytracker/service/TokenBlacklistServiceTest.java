package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TokenBlacklistServiceTest {

  @Test
  void revokeAndIsRevoked_withoutRedis_usesLocalStoreOnly() {
    TokenBlacklistService service = new TokenBlacklistService(null);

    assertThat(service.isRevoked("some.jwt.token")).isFalse();

    service.revoke("some.jwt.token", Duration.ofMinutes(5).toMillis());

    assertThat(service.isRevoked("some.jwt.token")).isTrue();
    assertThat(service.isRevoked("a-different-token")).isFalse();
  }

  @Test
  void isRevoked_returnsFalseOnceLocalEntryExpires() throws InterruptedException {
    TokenBlacklistService service = new TokenBlacklistService(null);

    service.revoke("short-lived-token", 1);
    Thread.sleep(5);

    assertThat(service.isRevoked("short-lived-token")).isFalse();
  }

  @Test
  void revoke_alsoWritesToRedisWhenAvailable() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);

    TokenBlacklistService service = new TokenBlacklistService(redisTemplate);

    service.revoke("token-a", 60_000);

    verify(valueOps).set(anyString(), org.mockito.ArgumentMatchers.eq("revoked"), any(Duration.class));
  }

  @Test
  void revoke_stillMarksLocallyRevokedWhenRedisWriteFails() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    doThrow(new RuntimeException("redis down"))
        .when(valueOps)
        .set(anyString(), anyString(), any(Duration.class));

    TokenBlacklistService service = new TokenBlacklistService(redisTemplate);

    service.revoke("token-b", 60_000);

    // Local fallback still authoritative even though the Redis write blew up.
    assertThat(service.isRevoked("token-b")).isTrue();
  }

  @Test
  void isRevoked_checksRedisWhenNotFoundLocally() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    when(redisTemplate.hasKey(anyString())).thenReturn(true);

    TokenBlacklistService service = new TokenBlacklistService(redisTemplate);

    assertThat(service.isRevoked("token-known-only-to-redis")).isTrue();
  }

  @Test
  void isRevoked_returnsFalseWhenRedisCheckFails() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));

    TokenBlacklistService service = new TokenBlacklistService(redisTemplate);

    assertThat(service.isRevoked("unknown-token")).isFalse();
  }

  @Test
  void evictExpiredTokens_removesOnlyExpiredEntries() throws InterruptedException {
    TokenBlacklistService service = new TokenBlacklistService(null);

    service.revoke("expiring-soon", 1);
    service.revoke("still-valid", Duration.ofMinutes(5).toMillis());
    Thread.sleep(5);

    service.evictExpiredTokens();

    assertThat(service.isRevoked("still-valid")).isTrue();
    // Already evicted from the map, and naturally expired anyway -> still reads as not revoked.
    assertThat(service.isRevoked("expiring-soon")).isFalse();
  }
}
