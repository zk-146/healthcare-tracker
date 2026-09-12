package com.healthcare.activitytracker.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Stores a user's own DeepSeek API key (Profile &gt; AI settings), so AI digests/insights can be
 * generated through DeepSeek instead of the shared local Ollama instance ({@code
 * app.ai.provider=deepseek}).
 *
 * <p>Mirrors {@link GoogleHealthConnection} — a per-user external-service credential gets its own
 * table joined to {@code users}, rather than living as a column on {@link User} itself. The key is
 * encrypted at rest via {@code TokenCipher}, the same AES-256-GCM scheme Google Health's OAuth
 * tokens use.
 */
@Entity
@Table(
    name = "deepseek_connections",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_deepseek_connection_user",
          columnNames = {"user_id"})
    },
    indexes = {@Index(name = "idx_deepseek_connections_user_id", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeepSeekConnection {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "api_key_encrypted", nullable = false, columnDefinition = "TEXT")
  private String apiKeyEncrypted;

  @CreationTimestamp
  @Column(name = "connected_at", updatable = false)
  private LocalDateTime connectedAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
