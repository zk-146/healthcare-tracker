package com.healthcare.activitytracker.model.entity;

import com.healthcare.activitytracker.model.enums.GoalMetric;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "goals",
    indexes = {
      @Index(name = "idx_goals_user_id", columnList = "user_id"),
      @Index(name = "idx_goals_user_active", columnList = "user_id, active")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "metric", nullable = false, length = 50)
  private GoalMetric metric;

  @Column(name = "target_value", nullable = false)
  private double targetValue;

  @Builder.Default
  @Column(name = "active", nullable = false)
  private boolean active = true;

  /** Set to today when the daily goal is first achieved; reset to null on deactivation. */
  @Column(name = "last_achieved_date")
  private LocalDate lastAchievedDate;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
