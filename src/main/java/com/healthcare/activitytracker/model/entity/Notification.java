package com.healthcare.activitytracker.model.entity;

import com.healthcare.activitytracker.model.enums.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Persisted in-app notification delivered to a user when a meaningful event occurs (e.g. a streak
 * milestone is reached).
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
      @Index(name = "idx_notifications_user_id", columnList = "user_id"),
      @Index(name = "idx_notifications_user_unread", columnList = "user_id, is_read")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 50)
  private NotificationType type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 500)
  private String body;

  @Column(name = "is_read", nullable = false)
  @Builder.Default
  private boolean read = false;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;
}
