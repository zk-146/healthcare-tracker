package com.healthcare.activitytracker.model.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * Optimistic locking version counter. Prevents concurrent profile updates from silently
   * overwriting each other.
   */
  @Version
  @Column(nullable = false)
  private Long version;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  /**
   * Whether the user has confirmed ownership of their email address. Tracked but not currently
   * enforced at login (see {@code EmailVerificationService}).
   */
  @Builder.Default
  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified = false;

  @Column(name = "full_name", nullable = false, length = 150)
  private String fullName;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(length = 20)
  private String gender;

  @Column(name = "height_cm")
  private Double heightCm;

  @Column(name = "weight_kg")
  private Double weightKg;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
