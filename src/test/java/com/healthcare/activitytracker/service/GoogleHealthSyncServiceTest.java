package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.integration.ImportedWorkout;
import com.healthcare.activitytracker.repository.GoogleHealthConnectionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleHealthSyncServiceTest {

  @Mock private GoogleHealthConnectionRepository connectionRepository;
  @Mock private GoogleHealthConnectionService connectionService;
  @Mock private GoogleHealthClient client;
  @Mock private ActivityService activityService;

  private final GoogleHealthProperties properties = new GoogleHealthProperties();
  private GoogleHealthSyncService syncService;

  private final UUID userId = UUID.randomUUID();
  private GoogleHealthConnection connection;

  @BeforeEach
  void setUp() {
    properties.setDeviceLabel("fitbit-charge-6");
    properties.setInitialBackfillDays(30);
    syncService =
        new GoogleHealthSyncService(
            connectionRepository, connectionService, client, activityService, properties);

    User user = User.builder().id(userId).email("o@example.com").build();
    connection = GoogleHealthConnection.builder().id(UUID.randomUUID()).user(user).build();
  }

  @Test
  void importsNewWorkoutsAndAdvancesWatermark() {
    when(connectionService.getFreshAccessToken(connection)).thenReturn("access-token");
    LocalDateTime start = LocalDateTime.now().minusHours(2);
    ImportedWorkout workout =
        ImportedWorkout.builder().externalId("rec-1").rawType("RUN").startedAt(start).build();
    when(client.fetchWorkoutsSince(eq("access-token"), any())).thenReturn(List.of(workout));
    when(activityService.importWorkout(eq(userId), eq(workout), anyString())).thenReturn(true);

    int imported = syncService.syncConnection(connection);

    assertThat(imported).isEqualTo(1);
    verify(activityService).importWorkout(userId, workout, "fitbit-charge-6");

    ArgumentCaptor<GoogleHealthConnection> captor =
        ArgumentCaptor.forClass(GoogleHealthConnection.class);
    verify(connectionRepository).save(captor.capture());
    assertThat(captor.getValue().getLastSyncedAt()).isEqualTo(start);
  }

  @Test
  void duplicateWorkoutsAreNotCounted() {
    when(connectionService.getFreshAccessToken(connection)).thenReturn("access-token");
    ImportedWorkout workout =
        ImportedWorkout.builder()
            .externalId("rec-dup")
            .rawType("WALK")
            .startedAt(LocalDateTime.now().minusHours(1))
            .build();
    when(client.fetchWorkoutsSince(anyString(), any())).thenReturn(List.of(workout));
    when(activityService.importWorkout(eq(userId), eq(workout), anyString())).thenReturn(false);

    int imported = syncService.syncConnection(connection);

    assertThat(imported).isZero();
  }

  @Test
  void usesBackfillWindowWhenNeverSynced() {
    when(connectionService.getFreshAccessToken(connection)).thenReturn("access-token");
    when(client.fetchWorkoutsSince(anyString(), any())).thenReturn(List.of());

    syncService.syncConnection(connection);

    ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(client).fetchWorkoutsSince(eq("access-token"), since.capture());
    // ~30 days back, allow a small execution window
    assertThat(since.getValue()).isBefore(LocalDateTime.now().minusDays(29));
    assertThat(since.getValue()).isAfter(LocalDateTime.now().minusDays(31));
  }

  @Test
  void scheduledSyncIsInertWhenDisabled() {
    properties.setEnabled(false);

    syncService.scheduledSync();

    verify(connectionRepository, never()).findByStatus(any());
  }
}
