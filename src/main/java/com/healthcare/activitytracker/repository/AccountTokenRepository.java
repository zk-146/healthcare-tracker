package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.AccountToken;
import com.healthcare.activitytracker.model.enums.AccountTokenPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

  Optional<AccountToken> findByTokenHash(String tokenHash);

  /**
   * Atomically claims a token: marks it used only if it is the right kind, unused and unexpired.
   * Returns 0 otherwise — including for a second request racing on the same link — so a link can be
   * redeemed exactly once however many times it is submitted.
   */
  @Modifying
  @Query(
      "UPDATE AccountToken t SET t.usedAt = :now "
          + "WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose "
          + "AND t.usedAt IS NULL AND t.expiresAt > :now")
  int claim(
      @Param("tokenHash") String tokenHash,
      @Param("purpose") AccountTokenPurpose purpose,
      @Param("now") LocalDateTime now);

  /** Whether a token of this kind was issued to the user after the given instant (resend limit). */
  @Query(
      "SELECT COUNT(t) > 0 FROM AccountToken t "
          + "WHERE t.user.id = :userId AND t.purpose = :purpose AND t.createdAt > :since")
  boolean existsIssuedSince(
      @Param("userId") UUID userId,
      @Param("purpose") AccountTokenPurpose purpose,
      @Param("since") LocalDateTime since);

  /** Invalidates every outstanding link of one kind for a user. */
  @Modifying
  @Query("DELETE FROM AccountToken t WHERE t.user.id = :userId AND t.purpose = :purpose")
  int deleteAllByUserIdAndPurpose(
      @Param("userId") UUID userId, @Param("purpose") AccountTokenPurpose purpose);

  /** Delete all of a user's tokens (account deletion). */
  @Modifying
  @Query("DELETE FROM AccountToken t WHERE t.user.id = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);

  /** Clean up expired tokens. */
  @Modifying
  @Query("DELETE FROM AccountToken t WHERE t.expiresAt < :now")
  int deleteExpired(@Param("now") LocalDateTime now);
}
