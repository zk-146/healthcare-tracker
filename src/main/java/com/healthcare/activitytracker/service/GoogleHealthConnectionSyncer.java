package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.model.integration.ImportedWorkout;
import com.healthcare.activitytracker.repository.GoogleHealthConnectionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs a single Google Health connection.
 *
 * <p>Its own bean, rather than a method on {@link GoogleHealthSyncService}, so that both callers —
 * the scheduler and the manual-trigger endpoint — reach it through the Spring proxy and so actually
 * get the {@code @Transactional} below. When this lived on the scheduler, {@code scheduledSync()}
 * called it via {@code this}, which bypasses the proxy and silently disabled the annotation.
 */
@Service
public class GoogleHealthConnectionSyncer {

  private static final Logger log = LoggerFactory.getLogger(GoogleHealthConnectionSyncer.class);

  private final GoogleHealthConnectionRepository connectionRepository;
  private final GoogleHealthConnectionService connectionService;
  private final GoogleHealthClient client;
  private final ActivityService activityService;
  private final GoogleHealthProperties properties;

  public GoogleHealthConnectionSyncer(
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
   * Fetches workouts since the watermark, imports the new ones, then advances the watermark.
   * Imports are idempotent, so an overlapping window is safe.
   *
   * @param connection the connection to sync
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
    UUID userId = connection.getUser().getId();

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
