package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TokenBlacklistServiceTest {

  private static final String TOKEN = "some.jwt.token";

  @Test
  void inMemory_revokeThenIsRevoked_returnsTrue() {
    TokenBlacklistService service = new TokenBlacklistService(null);

    service.revoke(TOKEN, 60_000);

    assertThat(service.isRevoked(TOKEN)).isTrue();
  }

  @Test
  void inMemory_unknownToken_isNotRevoked() {
    TokenBlacklistService service = new TokenBlacklistService(null);

    assertThat(service.isRevoked("never-seen")).isFalse();
  }

  @Test
  void inMemory_expiredEntry_isNotRevoked() {
    TokenBlacklistService service = new TokenBlacklistService(null);

    // Already-expired TTL: the entry should be treated as not revoked.
    service.revoke(TOKEN, -1);

    assertThat(service.isRevoked(TOKEN)).isFalse();
  }

  @Test
  void redis_revoke_writesToRedis() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);

    TokenBlacklistService service = new TokenBlacklistService(redis);
    service.revoke(TOKEN, 60_000);

    org.mockito.Mockito.verify(valueOps).set(any(), eq("revoked"), any(Duration.class));
  }

  @Test
  void redis_isRevoked_trueWhenKeyPresent() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.hasKey(any())).thenReturn(true);

    TokenBlacklistService service = new TokenBlacklistService(redis);

    assertThat(service.isRevoked(TOKEN)).isTrue();
  }

  @Test
  void redis_readFailureAfterRevoke_fallsBackToLocalCache() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    // Revoke succeeds (writes Redis + local), but a later read throws.
    when(redis.hasKey(any())).thenThrow(new RuntimeException("connection reset"));

    TokenBlacklistService service = new TokenBlacklistService(redis);
    service.revoke(TOKEN, 60_000);

    // The dual-write to the local cache means the revocation is still recognised.
    assertThat(service.isRevoked(TOKEN)).isTrue();
  }

  @Test
  void redis_keyAbsentButLocallyRevoked_isStillRevoked() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    // Redis reports the key as absent (e.g. revoked only locally during a prior outage)...
    when(redis.hasKey(any())).thenReturn(false);

    TokenBlacklistService service = new TokenBlacklistService(redis);
    service.revoke(TOKEN, 60_000);

    // ...but the local safety-net still catches it.
    assertThat(service.isRevoked(TOKEN)).isTrue();
  }
}
