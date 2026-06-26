package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** Revoke all active refresh tokens for a user (e.g. on password change or security incident). */
  @Modifying
  @Query(
      "UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
  int revokeAllByUserId(@Param("userId") UUID userId);

  /** Clean up expired tokens. */
  @Modifying
  @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
  int deleteExpired(@Param("now") LocalDateTime now);

  /** Delete all refresh tokens for a user (e.g. account erasure). */
  @Modifying
  @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
  int deleteByUserId(@Param("userId") UUID userId);
}
