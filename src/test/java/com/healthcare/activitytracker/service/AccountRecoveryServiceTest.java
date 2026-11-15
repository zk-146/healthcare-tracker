package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.config.MailProperties;
import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.entity.AccountToken;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.AccountTokenPurpose;
import com.healthcare.activitytracker.repository.AccountTokenRepository;
import com.healthcare.activitytracker.repository.RefreshTokenRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
  private static final LocalDateTime NOW_UTC = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
  private static final String EMAIL = "user@example.com";

  @Mock private UserRepository userRepository;
  @Mock private AccountTokenRepository tokenRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private LoginAttemptService loginAttemptService;
  @Mock private EmailSender emailSender;

  private MailProperties mailProperties;
  private AccountRecoveryService service;
  private User user;

  @BeforeEach
  void setUp() {
    mailProperties = new MailProperties();
    mailProperties.setPublicBaseUrl("https://tracker.example.com/");
    service =
        new AccountRecoveryService(
            userRepository,
            tokenRepository,
            refreshTokenRepository,
            passwordEncoder,
            loginAttemptService,
            emailSender,
            mailProperties,
            Clock.fixed(NOW, ZoneOffset.UTC));
    user =
        User.builder()
            .id(UUID.randomUUID())
            .email(EMAIL)
            .passwordHash("old-hash")
            .fullName("Test User")
            .build();
  }

  private AccountToken savedToken() {
    ArgumentCaptor<AccountToken> captor = ArgumentCaptor.forClass(AccountToken.class);
    verify(tokenRepository).save(captor.capture());
    return captor.getValue();
  }

  private String emailedBody() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(emailSender).send(eq(EMAIL), anyString(), captor.capture());
    return captor.getValue();
  }

  private static String tokenIn(String body, String fragmentKey) {
    Matcher matcher = Pattern.compile("/#" + fragmentKey + "=([A-Za-z0-9_-]+)").matcher(body);
    assertThat(matcher.find()).as("link in email body").isTrue();
    return matcher.group(1);
  }

  @Test
  void requestPasswordReset_emailsALinkAndStoresOnlyTheTokensHash() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

    service.requestPasswordReset("  User@Example.com ");

    String body = emailedBody();
    assertThat(body).contains("https://tracker.example.com/#reset-password=");
    String rawToken = tokenIn(body, "reset-password");
    AccountToken stored = savedToken();
    assertThat(stored.getTokenHash())
        .isEqualTo(AuthService.sha256(rawToken))
        .isNotEqualTo(rawToken);
    assertThat(stored.getPurpose()).isEqualTo(AccountTokenPurpose.PASSWORD_RESET);
    assertThat(stored.getCreatedAt()).isEqualTo(NOW_UTC);
    assertThat(stored.getExpiresAt()).isEqualTo(NOW_UTC.plusMinutes(30));
    assertThat(stored.getUsedAt()).isNull();
  }

  @Test
  void requestPasswordReset_doesNothingForAnUnknownAddress() {
    when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

    service.requestPasswordReset("nobody@example.com");

    verifyNoInteractions(tokenRepository, emailSender);
  }

  @Test
  void requestPasswordReset_isSuppressedInsideTheCooldown() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    when(tokenRepository.existsIssuedSince(
            user.getId(), AccountTokenPurpose.PASSWORD_RESET, NOW_UTC.minusSeconds(60)))
        .thenReturn(true);

    service.requestPasswordReset(EMAIL);

    verify(tokenRepository, never()).save(any());
    verifyNoInteractions(emailSender);
  }

  @Test
  void resetPassword_setsThePasswordAndEndsEveryOtherSession() {
    String hash = AuthService.sha256("raw-token");
    when(tokenRepository.claim(hash, AccountTokenPurpose.PASSWORD_RESET, NOW_UTC)).thenReturn(1);
    when(tokenRepository.findByTokenHash(hash))
        .thenReturn(Optional.of(AccountToken.builder().user(user).build()));
    when(passwordEncoder.encode("NewP@ssword456")).thenReturn("new-hash");

    service.resetPassword("raw-token", "NewP@ssword456");

    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    assertThat(user.isEmailVerified()).isTrue();
    verify(userRepository).save(user);
    verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    verify(tokenRepository)
        .deleteAllByUserIdAndPurpose(user.getId(), AccountTokenPurpose.PASSWORD_RESET);
    verify(loginAttemptService).clearFailures(EMAIL);
    assertThat(emailedBody()).contains("was just changed");
  }

  @Test
  void resetPassword_rejectsATokenThatCannotBeClaimed() {
    when(tokenRepository.claim(anyString(), eq(AccountTokenPurpose.PASSWORD_RESET), eq(NOW_UTC)))
        .thenReturn(0);

    assertThatThrownBy(() -> service.resetPassword("used-or-expired", "NewP@ssword456"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(AccountRecoveryService.INVALID_LINK);

    verifyNoInteractions(userRepository, refreshTokenRepository, emailSender, passwordEncoder);
  }

  @Test
  void sendVerificationEmail_emailsALinkValidForADay() {
    service.sendVerificationEmail(user);

    String body = emailedBody();
    assertThat(body).contains("https://tracker.example.com/#verify-email=");
    AccountToken stored = savedToken();
    assertThat(stored.getTokenHash()).isEqualTo(AuthService.sha256(tokenIn(body, "verify-email")));
    assertThat(stored.getPurpose()).isEqualTo(AccountTokenPurpose.EMAIL_VERIFICATION);
    assertThat(stored.getExpiresAt()).isEqualTo(NOW_UTC.plusHours(24));
  }

  @Test
  void sendVerificationEmail_isANoOpOnceVerified() {
    user.setEmailVerified(true);

    service.sendVerificationEmail(user);

    verifyNoInteractions(tokenRepository, emailSender);
  }

  @Test
  void resendVerification_sendsToTheSignedInUser() {
    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

    service.resendVerification(user.getId());

    assertThat(emailedBody()).contains("#verify-email=");
  }

  @Test
  void resendVerification_failsForAnUnknownUser() {
    UUID unknown = UUID.randomUUID();
    when(userRepository.findById(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resendVerification(unknown))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void verifyEmail_marksTheAccountVerified() {
    String hash = AuthService.sha256("raw-token");
    when(tokenRepository.claim(hash, AccountTokenPurpose.EMAIL_VERIFICATION, NOW_UTC))
        .thenReturn(1);
    when(tokenRepository.findByTokenHash(hash))
        .thenReturn(Optional.of(AccountToken.builder().user(user).build()));

    service.verifyEmail("raw-token");

    assertThat(user.isEmailVerified()).isTrue();
    verify(userRepository).save(user);
    verify(tokenRepository)
        .deleteAllByUserIdAndPurpose(user.getId(), AccountTokenPurpose.EMAIL_VERIFICATION);
  }

  @Test
  void verifyEmail_rejectsATokenThatCannotBeClaimed() {
    when(tokenRepository.claim(
            anyString(), eq(AccountTokenPurpose.EMAIL_VERIFICATION), eq(NOW_UTC)))
        .thenReturn(0);

    assertThatThrownBy(() -> service.verifyEmail("nope"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(AccountRecoveryService.INVALID_LINK);

    verifyNoInteractions(userRepository);
  }

  @Test
  void cleanupExpiredTokens_deletesAsOfNow() {
    when(tokenRepository.deleteExpired(NOW_UTC)).thenReturn(3);

    service.cleanupExpiredTokens();

    verify(tokenRepository).deleteExpired(NOW_UTC);
  }
}
