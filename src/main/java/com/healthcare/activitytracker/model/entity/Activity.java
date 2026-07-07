package com.healthcare.activitytracker.model.entity;

import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "activities",
    indexes = {
      @Index(name = "idx_activities_user_date", columnList = "user_id, started_at DESC"),
      @Index(name = "idx_activities_source", columnList = "source")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Activity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * Optimistic locking version counter. Concurrent updates to the same activity row will throw
   * ObjectOptimisticLockingFailureException rather than silently overwriting.
   */
  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "activity_type", nullable = false)
  private ActivityType activityType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ActivitySource source;

  @Column(name = "device_id", length = 100)
  private String deviceId;

  /**
   * Identifier of the originating record in an external system (e.g. a Google Health workout
   * session). Null for activities entered manually. Used to de-duplicate repeated imports.
   */
  @Column(name = "external_id", length = 255)
  private String externalId;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "ended_at")
  private LocalDateTime endedAt;

  @Column(name = "duration_minutes")
  private Integer durationMinutes;

  @Column(name = "distance_km")
  private Double distanceKm;

  @Column(name = "calories_burned")
  private Double caloriesBurned;

  @Column(name = "heart_rate_avg")
  private Integer heartRateAvg;

  private Integer steps;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
