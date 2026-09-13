package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.config.GoogleHealthProperties;
import com.healthcare.activitytracker.model.entity.GoogleHealthConnection;
import com.healthcare.activitytracker.model.enums.ConnectionStatus;
import com.healthcare.activitytracker.repository.GoogleHealthConnectionRepository;
import com.healthcare.activitytracker.service.GoogleHealthOAuthService.RefreshTokenRevokedException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically pulls new Fitbit Charge 6 workouts from the Google Health API and imports them as
 * activities. For a single personal user we poll on a fixed delay rather than running a public
 * webhook receiver, so the app needs no inbound network exposure.
 *
 * <p>The per-connection work lives in {@link GoogleHealthConnectionSyncer} so that it is reached
 * through the Spring proxy and its {@code @Transactional} applies — see that class for why.
 */
@Service
public class GoogleHealthSyncService {

  private static final Logger log = LoggerFactory.getLogger(GoogleHealthSyncService.class);

  private final GoogleHealthConnectionRepository connectionRepository;
  private final GoogleHealthConnectionSyncer syncer;
  private final GoogleHealthProperties properties;

  public GoogleHealthSyncService(
      GoogleHealthConnectionRepository connectionRepository,
      GoogleHealthConnectionSyncer syncer,
      GoogleHealthProperties properties) {
    this.connectionRepository = connectionRepository;
    this.syncer = syncer;
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
        syncer.syncConnection(connection);
      } catch (RefreshTokenRevokedException e) {
        // Already handled (status + notification) by the connection service.
        log.info("Skipping sync for user {} — reconnect required", connection.getUser().getId());
      } catch (Exception e) {
        log.error("Google Health sync failed for user {}", connection.getUser().getId(), e);
      }
    }
  }
}
