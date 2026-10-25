package com.healthcare.activitytracker.model.entity;

import com.healthcare.activitytracker.model.enums.GoalMetric;
import com.healthcare.activitytracker.model.enums.GoalPeriod;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A target the user set for themselves: so many {@link GoalMetric} per {@link GoalPeriod}.
 *
 * <p>Retirement is a soft archive ({@code active = false}) rather than a delete, because the
 * uniqueness rule is scoped to active rows — see the partial index in {@code
 * V6__activity_goals.sql}. Keeping the row also leaves a record that the target once existed.
 */
@Entity
@Table(
    name = "activity_goals",
    indexes = {@Index(name = "idx_activity_goals_user_id", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityGoal {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * Optimistic locking version counter. Two tabs editing the same goal's target produce an
   * ObjectOptimisticLockingFailureException (surfaced as 409) rather than a silent last-write-wins.
   */
  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GoalMetric metric;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private GoalPeriod period;

  @Column(name = "target_value", nullable = false)
  private Double targetValue;

  /** False once retired. The uniqueness constraint applies only to active rows. */
  @Column(nullable = false)
  private boolean active;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
