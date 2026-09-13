package com.healthcare.activitytracker.service;

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
import com.healthcare.activitytracker.service.GoogleHealthOAuthService.RefreshTokenRevokedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleHealthSyncServiceTest {

  @Mock private GoogleHealthConnectionRepository connectionRepository;
  @Mock private GoogleHealthConnectionSyncer syncer;

  private final GoogleHealthProperties properties = new GoogleHealthProperties();
  private GoogleHealthSyncService syncService;

  @BeforeEach
  void setUp() {
    properties.setDeviceLabel("fitbit-charge-6");
    properties.setInitialBackfillDays(30);
    syncService = new GoogleHealthSyncService(connectionRepository, syncer, properties);
  }

  private GoogleHealthConnection newConnection() {
    User user = User.builder().id(UUID.randomUUID()).email("o@example.com").build();
    return GoogleHealthConnection.builder().id(UUID.randomUUID()).user(user).build();
  }

  @Test
  void scheduledSyncIsInertWhenDisabled() {
    properties.setEnabled(false);

    syncService.scheduledSync();

    verify(connectionRepository, never()).findByStatus(any());
    verifyNoInteractions(syncer);
  }

  @Test
  void scheduledSync_delegatesToTheSyncerBeanForEachConnectedConnection() {
    properties.setEnabled(true);
    GoogleHealthConnection first = newConnection();
    GoogleHealthConnection second = newConnection();
    when(connectionRepository.findByStatus(ConnectionStatus.CONNECTED))
        .thenReturn(List.of(first, second));

    syncService.scheduledSync();

    verify(syncer).syncConnection(first);
    verify(syncer).syncConnection(second);
  }

  @Test
  void scheduledSync_continuesToTheNextConnectionWhenOneThrows() {
    properties.setEnabled(true);
    GoogleHealthConnection failing = newConnection();
    GoogleHealthConnection healthy = newConnection();
    when(connectionRepository.findByStatus(ConnectionStatus.CONNECTED))
        .thenReturn(List.of(failing, healthy));
    when(syncer.syncConnection(failing)).thenThrow(new IllegalStateException("boom"));

    syncService.scheduledSync();

    verify(syncer).syncConnection(healthy);
  }

  @Test
  void scheduledSync_swallowsARevokedRefreshTokenAndContinues() {
    properties.setEnabled(true);
    GoogleHealthConnection revoked = newConnection();
    GoogleHealthConnection healthy = newConnection();
    when(connectionRepository.findByStatus(ConnectionStatus.CONNECTED))
        .thenReturn(List.of(revoked, healthy));
    when(syncer.syncConnection(revoked))
        .thenThrow(new RefreshTokenRevokedException("revoked", new RuntimeException()));

    syncService.scheduledSync();

    verify(syncer).syncConnection(healthy);
  }
}
