package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.entity.DeepSeekConnection;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.repository.DeepSeekConnectionRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import com.healthcare.activitytracker.util.TokenCipher;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeepSeekConnectionServiceTest {

  @Mock private DeepSeekConnectionRepository connectionRepository;
  @Mock private UserRepository userRepository;
  @Mock private TokenCipher tokenCipher;

  private DeepSeekConnectionService service;
  private final UUID userId = UUID.randomUUID();
  private User user;

  @BeforeEach
  void setUp() {
    service = new DeepSeekConnectionService(connectionRepository, userRepository, tokenCipher);
    user = User.builder().id(userId).email("jane@example.com").build();
  }

  @Test
  void saveApiKey_encryptsAndCreatesANewConnection_whenNoneExists() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(connectionRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(tokenCipher.encrypt("sk-my-key")).thenReturn("cipher-blob");

    service.saveApiKey(userId, "sk-my-key");

    ArgumentCaptor<DeepSeekConnection> captor = ArgumentCaptor.forClass(DeepSeekConnection.class);
    verify(connectionRepository).save(captor.capture());
    assertThat(captor.getValue().getApiKeyEncrypted()).isEqualTo("cipher-blob");
    assertThat(captor.getValue().getUser()).isEqualTo(user);
  }

  @Test
  void saveApiKey_trimsWhitespace_beforeEncrypting() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(connectionRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(tokenCipher.encrypt("sk-my-key")).thenReturn("cipher-blob");

    service.saveApiKey(userId, "  sk-my-key\n");

    verify(tokenCipher).encrypt("sk-my-key");
  }

  @Test
  void saveApiKey_replacesTheExistingConnection() {
    DeepSeekConnection existing =
        DeepSeekConnection.builder().id(UUID.randomUUID()).user(user).apiKeyEncrypted("old").build();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(connectionRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
    when(tokenCipher.encrypt("sk-new-key")).thenReturn("new-cipher-blob");

    service.saveApiKey(userId, "sk-new-key");

    assertThat(existing.getApiKeyEncrypted()).isEqualTo("new-cipher-blob");
    verify(connectionRepository).save(existing);
  }

  @Test
  void saveApiKey_throwsWhenUserNotFound() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.saveApiKey(userId, "sk-my-key"))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(connectionRepository, never()).save(any());
  }

  @Test
  void disconnect_removesTheConnection_whenOneExists() {
    DeepSeekConnection existing = DeepSeekConnection.builder().id(UUID.randomUUID()).build();
    when(connectionRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

    service.disconnect(userId);

    verify(connectionRepository).delete(existing);
  }

  @Test
  void disconnect_isANoOp_whenNoConnectionExists() {
    when(connectionRepository.findByUserId(userId)).thenReturn(Optional.empty());

    service.disconnect(userId);

    verify(connectionRepository, never()).delete(any());
  }

  @Test
  void isConnected_reflectsWhetherAConnectionRowExists() {
    when(connectionRepository.findByUserId(userId))
        .thenReturn(Optional.of(DeepSeekConnection.builder().build()))
        .thenReturn(Optional.empty());

    assertThat(service.isConnected(userId)).isTrue();
    assertThat(service.isConnected(userId)).isFalse();
  }

  @Test
  void findDecryptedApiKey_decryptsTheStoredValue() {
    DeepSeekConnection connection =
        DeepSeekConnection.builder().apiKeyEncrypted("cipher-blob").build();
    when(connectionRepository.findByUserId(userId)).thenReturn(Optional.of(connection));
    when(tokenCipher.decrypt("cipher-blob")).thenReturn("sk-my-key");

    assertThat(service.findDecryptedApiKey(userId)).hasValue("sk-my-key");
  }

  @Test
  void findDecryptedApiKey_returnsEmpty_whenNoConnection() {
    when(connectionRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThat(service.findDecryptedApiKey(userId)).isEmpty();
  }

  @Test
  void findDecryptedApiKey_returnsEmpty_whenUserIdIsNull() {
    assertThat(service.findDecryptedApiKey(null)).isEmpty();
  }
}
