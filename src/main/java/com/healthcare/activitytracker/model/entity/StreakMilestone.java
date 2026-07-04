package com.healthcare.activitytracker.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "streak_milestones",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_streak_milestone_user_days",
          columnNames = {"user_id", "milestone_days"})
    },
    indexes = {@Index(name = "idx_streak_milestones_user_id", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreakMilestone {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "milestone_days", nullable = false)
  private Integer milestoneDays;

  @Column(name = "achieved_at", nullable = false)
  private LocalDateTime achievedAt;

  /**
   * ID of the activity whose creation triggered this milestone. Intentionally <em>not</em> a
   * foreign key: milestones are historical records and must survive deletion of the referenced
   * activity, so a dangling ID here is expected and harmless.
   */
  @Column(name = "triggering_activity_id", nullable = false)
  private UUID triggeringActivityId;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;
}
