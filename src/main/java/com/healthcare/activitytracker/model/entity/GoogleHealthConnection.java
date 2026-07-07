package com.healthcare.activitytracker.model.entity;

import com.healthcare.activitytracker.model.enums.ConnectionStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Stores a user's link to the Google Health API (the successor to the Fitbit Web API), through
 * which Fitbit Charge 6 data is imported.
 *
 * <p>OAuth tokens are persisted encrypted at rest. Because this is a personal, single-user
 * integration running with a consumer Google account in OAuth "Testing" mode, the refresh token is
 * revoked by Google roughly every 7 days; when that happens {@link #status} is moved to {@link
 * ConnectionStatus#NEEDS_RECONNECT} and the owner is prompted to re-authorize.
 */
@Entity
@Table(
    name = "google_health_connections",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_google_health_connection_user",
          columnNames = {"user_id"})
    },
    indexes = {@Index(name = "idx_google_health_connections_user_id", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleHealthConnection {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  /** Short-lived (~1 hour) OAuth access token, encrypted at rest. */
  @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
  private String accessToken;

  /** Long(er)-lived OAuth refresh token, encrypted at rest. */
  @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
  private String refreshToken;

  @Column(name = "token_expires_at", nullable = false)
  private LocalDateTime tokenExpiresAt;

  @Column(columnDefinition = "TEXT")
  private String scopes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConnectionStatus status;

  /** Watermark: only workouts that started after this instant are pulled on the next sync. */
  @Column(name = "last_synced_at")
  private LocalDateTime lastSyncedAt;

  @CreationTimestamp
  @Column(name = "connected_at", updatable = false)
  private LocalDateTime connectedAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
