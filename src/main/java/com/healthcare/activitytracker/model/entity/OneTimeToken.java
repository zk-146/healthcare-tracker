package com.healthcare.activitytracker.model.entity;

import com.healthcare.activitytracker.model.enums.TokenPurpose;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Single-use token backing the email-verification and password-reset flows. Only the SHA-256 hash
 * of the raw token is persisted; the raw value is delivered to the user out of band (email) and
 * never stored.
 */
@Entity
@Table(
    name = "one_time_tokens",
    indexes = {
      @Index(name = "idx_one_time_tokens_token_hash", columnList = "token_hash"),
      @Index(name = "idx_one_time_tokens_user", columnList = "user_id"),
      @Index(name = "idx_one_time_tokens_expires_at", columnList = "expires_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OneTimeToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TokenPurpose purpose;

  @Column(nullable = false)
  private boolean used;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;
}
