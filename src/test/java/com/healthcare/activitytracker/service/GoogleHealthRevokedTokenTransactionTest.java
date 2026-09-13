package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ConnectionStatus;
import com.healthcare.activitytracker.repository.GoogleHealthConnectionRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import com.healthcare.activitytracker.service.GoogleHealthOAuthService.RefreshTokenRevokedException;
import com.healthcare.activitytracker.util.TokenCipher;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Proves the commit-versus-rollback decision at each transactional boundary on the revoked
 * refresh-token path.
 *
 * <p>Calls go through Spring's real {@link TransactionInterceptor}, reading the real
 * {@code @Transactional} attributes, in front of a mocked {@link PlatformTransactionManager}. The
 * interceptor asks the manager to {@code commit} or {@code rollback} based on those attributes, so
 * verifying which one it called is verifying the annotation's rollback rules — the thing a plain
 * Mockito unit test cannot see.
 */
@ExtendWith(MockitoExtension.class)
class GoogleHealthRevokedTokenTransactionTest {

  @Mock private PlatformTransactionManager txManager;
  @Mock private TransactionStatus txStatus;

  @Mock private GoogleHealthConnectionRepository connectionRepository;
  @Mock private UserRepository userRepository;
  @Mock private GoogleHealthOAuthService oauthService;
  @Mock private NotificationService notificationService;
  @Mock private TokenCipher tokenCipher;
  @Mock private GoogleHealthConnectionService connectionServiceMock;
  @Mock private GoogleHealthClient client;
  @Mock private ActivityService activityService;

  private final GoogleHealthProperties properties = new GoogleHealthProperties();
  private GoogleHealthConnection connection;

  @BeforeEach
  void setUp() {
    when(txManager.getTransaction(any())).thenReturn(txStatus);
    User user = User.builder().id(UUID.randomUUID()).email("o@example.com").build();
    connection =
        GoogleHealthConnection.builder()
            .id(UUID.randomUUID())
            .user(user)
            .accessToken("enc-access")
            .refreshToken("enc-refresh")
            .status(ConnectionStatus.CONNECTED)
            .tokenExpiresAt(LocalDateTime.now().minusMinutes(1))
            .build();
  }

  /** Wraps {@code target} in a proxy that applies its real {@code @Transactional} attributes. */
  @SuppressWarnings("unchecked")
  private <T> T transactional(T target) {
    ProxyFactory factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(
        new TransactionInterceptor(txManager, new AnnotationTransactionAttributeSource()));
    return (T) factory.getProxy();
  }

  @Test
  void getFreshAccessToken_commitsTheNeedsReconnectWrite_whenTheRefreshTokenIsRevoked() {
    GoogleHealthConnectionService service =
        transactional(
            new GoogleHealthConnectionService(
                connectionRepository,
                userRepository,
                oauthService,
                notificationService,
                tokenCipher,
                properties));
    when(tokenCipher.decrypt("enc-refresh")).thenReturn("plain-refresh");
    when(oauthService.refresh("plain-refresh"))
        .thenThrow(new RefreshTokenRevokedException("revoked", null));

    assertThatThrownBy(() -> service.getFreshAccessToken(connection))
        .isInstanceOf(RefreshTokenRevokedException.class);

    assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.NEEDS_RECONNECT);
    verify(connectionRepository).save(connection);
    verify(txManager).commit(txStatus);
    verify(txManager, never()).rollback(any());
  }

  @Test
  void syncConnection_commitsRatherThanRollingBack_whenTheRefreshTokenIsRevoked() {
    GoogleHealthConnectionSyncer syncer =
        transactional(
            new GoogleHealthConnectionSyncer(
                connectionRepository, connectionServiceMock, client, activityService, properties));
    when(connectionServiceMock.getFreshAccessToken(connection))
        .thenThrow(new RefreshTokenRevokedException("revoked", null));

    assertThatThrownBy(() -> syncer.syncConnection(connection))
        .isInstanceOf(RefreshTokenRevokedException.class);

    verify(txManager).commit(txStatus);
    verify(txManager, never()).rollback(any());
    // Thrown before any import or watermark update, so committing writes nothing else.
    verifyNoInteractions(client, activityService);
    verify(connectionRepository, never()).save(any());
  }

  /**
   * Control: proves this harness can observe a rollback at all. Without it, the two tests above
   * could pass on a proxy that never consulted the transaction attributes.
   */
  @Test
  void syncConnection_stillRollsBack_onAnyOtherRuntimeException() {
    GoogleHealthConnectionSyncer syncer =
        transactional(
            new GoogleHealthConnectionSyncer(
                connectionRepository, connectionServiceMock, client, activityService, properties));
    when(connectionServiceMock.getFreshAccessToken(connection))
        .thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> syncer.syncConnection(connection))
        .isInstanceOf(IllegalStateException.class);

    verify(txManager).rollback(txStatus);
    verify(txManager, never()).commit(any());
  }
}
