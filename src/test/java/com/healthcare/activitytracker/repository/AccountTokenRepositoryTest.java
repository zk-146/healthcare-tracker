package com.healthcare.activitytracker.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.activitytracker.model.entity.AccountToken;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.AccountTokenPurpose;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AccountTokenRepositoryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

  @Autowired private AccountTokenRepository tokenRepository;
  @Autowired private UserRepository userRepository;

  private User user;

  @BeforeEach
  void setUp() {
    user =
        userRepository.save(
            User.builder()
                .email("tokens.repo@example.com")
                .passwordHash("hash")
                .fullName("Token User")
                .build());
  }

  private void save(String hash, AccountTokenPurpose purpose, LocalDateTime expiresAt) {
    tokenRepository.saveAndFlush(
        AccountToken.builder()
            .user(user)
            .purpose(purpose)
            .tokenHash(hash)
            .createdAt(NOW.minusMinutes(5))
            .expiresAt(expiresAt)
            .build());
  }

  @Test
  void claimSucceedsExactlyOnce() {
    save("hash-once", AccountTokenPurpose.PASSWORD_RESET, NOW.plusMinutes(30));

    assertThat(tokenRepository.claim("hash-once", AccountTokenPurpose.PASSWORD_RESET, NOW))
        .isEqualTo(1);
    assertThat(tokenRepository.claim("hash-once", AccountTokenPurpose.PASSWORD_RESET, NOW))
        .isZero();
  }

  @Test
  void claimRejectsAnExpiredToken() {
    save("hash-expired", AccountTokenPurpose.PASSWORD_RESET, NOW.minusSeconds(1));

    assertThat(tokenRepository.claim("hash-expired", AccountTokenPurpose.PASSWORD_RESET, NOW))
        .isZero();
  }

  @Test
  void claimRejectsATokenIssuedForAnotherPurpose() {
    save("hash-purpose", AccountTokenPurpose.EMAIL_VERIFICATION, NOW.plusHours(1));

    assertThat(tokenRepository.claim("hash-purpose", AccountTokenPurpose.PASSWORD_RESET, NOW))
        .isZero();
  }

  @Test
  void claimRejectsAnUnknownToken() {
    assertThat(tokenRepository.claim("no-such-hash", AccountTokenPurpose.PASSWORD_RESET, NOW))
        .isZero();
  }

  @Test
  void existsIssuedSinceSeesOnlyRecentTokensOfTheSameKind() {
    save("hash-recent", AccountTokenPurpose.PASSWORD_RESET, NOW.plusMinutes(30));

    assertThat(
            tokenRepository.existsIssuedSince(
                user.getId(), AccountTokenPurpose.PASSWORD_RESET, NOW.minusMinutes(10)))
        .isTrue();
    assertThat(
            tokenRepository.existsIssuedSince(
                user.getId(), AccountTokenPurpose.PASSWORD_RESET, NOW.minusMinutes(1)))
        .isFalse();
    assertThat(
            tokenRepository.existsIssuedSince(
                user.getId(), AccountTokenPurpose.EMAIL_VERIFICATION, NOW.minusMinutes(10)))
        .isFalse();
  }

  @Test
  void deletesByPurposeByUserAndByExpiry() {
    save("hash-reset", AccountTokenPurpose.PASSWORD_RESET, NOW.plusMinutes(30));
    save("hash-verify", AccountTokenPurpose.EMAIL_VERIFICATION, NOW.plusHours(1));
    save("hash-old", AccountTokenPurpose.EMAIL_VERIFICATION, NOW.minusDays(1));

    assertThat(tokenRepository.deleteExpired(NOW)).isEqualTo(1);
    assertThat(
            tokenRepository.deleteAllByUserIdAndPurpose(
                user.getId(), AccountTokenPurpose.PASSWORD_RESET))
        .isEqualTo(1);
    assertThat(tokenRepository.deleteAllByUserId(user.getId())).isEqualTo(1);
    assertThat(tokenRepository.count()).isZero();
  }
}
