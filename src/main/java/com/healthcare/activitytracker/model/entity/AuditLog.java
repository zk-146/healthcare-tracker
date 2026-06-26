package com.healthcare.activitytracker.model.entity;

import com.healthcare.activitytracker.model.enums.AuditEventType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Immutable compliance audit record. One row per security- or data-relevant event. {@code userId}
 * is stored as a plain UUID (not a {@code @ManyToOne}) so audit rows survive even after the
 * referenced user is erased.
 */
@Entity
@Table(
    name = "audit_log",
    indexes = {
      @Index(name = "idx_audit_log_user_created", columnList = "user_id, created_at"),
      @Index(name = "idx_audit_log_event_type", columnList = "event_type")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** May be null for events not tied to a known user (e.g. a failed login for an unknown email). */
  @Column(name = "user_id")
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 50)
  private AuditEventType eventType;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(columnDefinition = "TEXT")
  private String detail;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;
}
