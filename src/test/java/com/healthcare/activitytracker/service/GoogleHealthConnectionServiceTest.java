package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ConnectionStatus;
import com.healthcare.activitytracker.repository.GoogleHealthConnectionRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import com.healthcare.activitytracker.service.GoogleHealthOAuthService.RefreshTokenRevokedException;
import com.healthcare.activitytracker.service.GoogleHealthOAuthService.TokenResponse;
import com.healthcare.activitytracker.util.TokenCipher;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleHealthConnectionServiceTest {

  @Mock private GoogleHealthConnectionRepository connectionRepository;
  @Mock private UserRepository userRepository;
  @Mock private GoogleHealthOAuthService oauthService;
  @Mock private NotificationService notificationService;
  @Mock private TokenCipher tokenCipher;

  private final GoogleHealthProperties properties = new GoogleHealthProperties();
  private GoogleHealthConnectionService service;

  private User user;
  private GoogleHealthConnection connection;

  @BeforeEach
  void setUp() {
    service =
        new GoogleHealthConnectionService(
            connectionRepository,
            userRepository,
            oauthService,
            notificationService,
            tokenCipher,
            properties);
    user = User.builder().id(UUID.randomUUID()).email("o@example.com").build();
    connection =
        GoogleHealthConnection.builder()
            .id(UUID.randomUUID())
            .user(user)
            .accessToken("enc-access")
            .refreshToken("enc-refresh")
            .status(ConnectionStatus.CONNECTED)
            .build();
  }

  @Test
  void returnsCurrentTokenWhenNotExpiring() {
    connection.setTokenExpiresAt(LocalDateTime.now().plusMinutes(30));
    when(tokenCipher.decrypt("enc-access")).thenReturn("plain-access");

    String token = service.getFreshAccessToken(connection);

    assertThat(token).isEqualTo("plain-access");
    verify(oauthService, never()).refresh(anyString());
  }

  @Test
  void refreshesWhenExpired() {
    connection.setTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
    when(tokenCipher.decrypt("enc-refresh")).thenReturn("plain-refresh");
    when(oauthService.refresh("plain-refresh"))
        .thenReturn(
            new TokenResponse(
                "new-access", "plain-refresh", LocalDateTime.now().plusHours(1), null));
    when(tokenCipher.encrypt(anyString())).thenReturn("enc-new");

    String token = service.getFreshAccessToken(connection);

    assertThat(token).isEqualTo("new-access");
    verify(connectionRepository).save(connection);
  }

  @Test
  void revokedRefreshTokenMarksNeedsReconnectAndNotifies() {
    connection.setTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
    when(tokenCipher.decrypt("enc-refresh")).thenReturn("plain-refresh");
    when(oauthService.refresh("plain-refresh"))
        .thenThrow(new RefreshTokenRevokedException("revoked", null));

    assertThatThrownBy(() -> service.getFreshAccessToken(connection))
        .isInstanceOf(RefreshTokenRevokedException.class);

    assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.NEEDS_RECONNECT);
    verify(connectionRepository).save(connection);
    verify(notificationService).sendReconnectReminder(user, "google-health");
  }

  @Test
  void completeConnectionPersistsEncryptedTokens() {
    UUID userId = user.getId();
    when(oauthService.consumeStateToUserId("state-1")).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
    when(oauthService.exchangeCode("code-1"))
        .thenReturn(
            new TokenResponse("access", "refresh", LocalDateTime.now().plusHours(1), "scope-a"));
    when(connectionRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());
    when(tokenCipher.encrypt("access")).thenReturn("enc-access");
    when(tokenCipher.encrypt("refresh")).thenReturn("enc-refresh");

    UUID result = service.completeConnection("code-1", "state-1");

    assertThat(result).isEqualTo(userId);
    org.mockito.ArgumentCaptor<GoogleHealthConnection> captor =
        org.mockito.ArgumentCaptor.forClass(GoogleHealthConnection.class);
    verify(connectionRepository).save(captor.capture());
    GoogleHealthConnection saved = captor.getValue();
    assertThat(saved.getAccessToken()).isEqualTo("enc-access");
    assertThat(saved.getRefreshToken()).isEqualTo("enc-refresh");
    assertThat(saved.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
  }

  @Test
  void completeConnectionRejectsMissingRefreshToken() {
    UUID userId = user.getId();
    when(oauthService.consumeStateToUserId("state-x")).thenReturn(userId);
    when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
    when(oauthService.exchangeCode("code-x"))
        .thenReturn(new TokenResponse("access", null, LocalDateTime.now().plusHours(1), null));

    assertThatThrownBy(() -> service.completeConnection("code-x", "state-x"))
        .isInstanceOf(IllegalStateException.class);
    verify(connectionRepository, never()).save(any());
  }
}
