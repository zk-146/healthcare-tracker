package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.model.enums.ConnectionStatus;
import com.healthcare.activitytracker.model.integration.ImportedWorkout;
import com.healthcare.activitytracker.repository.GoogleHealthConnectionRepository;
import com.healthcare.activitytracker.service.GoogleHealthOAuthService.RefreshTokenRevokedException;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically pulls new Fitbit Charge 6 workouts from the Google Health API and imports them as
 * activities. For a single personal user we poll on a fixed delay rather than running a public
 * webhook receiver, so the app needs no inbound network exposure.
 */
@Service
public class GoogleHealthSyncService {

  private static final Logger log = LoggerFactory.getLogger(GoogleHealthSyncService.class);

  private final GoogleHealthConnectionRepository connectionRepository;
  private final GoogleHealthConnectionService connectionService;
  private final GoogleHealthClient client;
  private final ActivityService activityService;
  private final GoogleHealthProperties properties;

  public GoogleHealthSyncService(
      GoogleHealthConnectionRepository connectionRepository,
      GoogleHealthConnectionService connectionService,
      GoogleHealthClient client,
      ActivityService activityService,
      GoogleHealthProperties properties) {
    this.connectionRepository = connectionRepository;
    this.connectionService = connectionService;
    this.client = client;
    this.activityService = activityService;
    this.properties = properties;
  }

  /**
   * Scheduled entry point. Disabled unless {@code app.integrations.google-health.enabled=true}; the
   * poll cadence is {@code app.integrations.google-health.poll-interval-ms} (default 1 hour).
   */
  @Scheduled(
      fixedDelayString = "${app.integrations.google-health.poll-interval-ms:3600000}",
      initialDelayString = "${app.integrations.google-health.initial-delay-ms:60000}")
  public void scheduledSync() {
    if (!properties.isEnabled()) {
      return;
    }
    List<GoogleHealthConnection> connections =
        connectionRepository.findByStatus(ConnectionStatus.CONNECTED);
    for (GoogleHealthConnection connection : connections) {
      try {
        syncConnection(connection);
      } catch (RefreshTokenRevokedException e) {
        // Already handled (status + notification) by the connection service.
        log.info("Skipping sync for user {} — reconnect required", connection.getUser().getId());
      } catch (Exception e) {
        log.error("Google Health sync failed for user {}", connection.getUser().getId(), e);
      }
    }
  }

  /**
   * Syncs a single connection: fetch workouts since the watermark, import the new ones, then
   * advance the watermark. Imports are idempotent, so an overlapping window is safe.
   *
   * @return the number of newly imported activities
   */
  @Transactional
  public int syncConnection(GoogleHealthConnection connection) {
    String accessToken = connectionService.getFreshAccessToken(connection);

    LocalDateTime since =
        connection.getLastSyncedAt() != null
            ? connection.getLastSyncedAt()
            : LocalDateTime.now().minusDays(properties.getInitialBackfillDays());

    List<ImportedWorkout> workouts = client.fetchWorkoutsSince(accessToken, since);
    java.util.UUID userId = connection.getUser().getId();

    int imported = 0;
    LocalDateTime maxStart = since;
    for (ImportedWorkout workout : workouts) {
      if (activityService.importWorkout(userId, workout, properties.getDeviceLabel())) {
        imported++;
      }
      if (workout.getStartedAt() != null && workout.getStartedAt().isAfter(maxStart)) {
        maxStart = workout.getStartedAt();
      }
    }

    connection.setLastSyncedAt(maxStart);
    connectionRepository.save(connection);
    log.info("Google Health sync for user {}: {} new activities imported", userId, imported);
    return imported;
  }
}
