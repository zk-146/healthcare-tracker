package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenBlacklistServiceTest {

  private final TokenBlacklistService service = new TokenBlacklistService(null); // in-memory

  @Test
  void revoke_thenIsRevoked_returnsTrueUntilExpiry() {
    String token = "some.jwt.token";
    assertThat(service.isRevoked(token)).isFalse();

    service.revoke(token, 60_000L);
    assertThat(service.isRevoked(token)).isTrue();
  }

  @Test
  void revokeAllForUser_invalidatesTokensIssuedBeforeCutoff() {
    String userId = UUID.randomUUID().toString();
    long now = System.currentTimeMillis();

    assertThat(service.isUserInvalidatedSince(userId, now)).isFalse();

    service.revokeAllForUser(userId, 900_000L);

    // A token issued before the logout cutoff is invalidated...
    assertThat(service.isUserInvalidatedSince(userId, now - 10_000L)).isTrue();
    // ...while a token issued afterwards (e.g. a fresh login) remains valid.
    assertThat(service.isUserInvalidatedSince(userId, now + 10_000L)).isFalse();
  }

  @Test
  void revokeAllForUser_doesNotAffectOtherUsers() {
    String userA = UUID.randomUUID().toString();
    String userB = UUID.randomUUID().toString();

    service.revokeAllForUser(userA, 900_000L);

    assertThat(service.isUserInvalidatedSince(userB, System.currentTimeMillis() - 10_000L))
        .isFalse();
  }
}
