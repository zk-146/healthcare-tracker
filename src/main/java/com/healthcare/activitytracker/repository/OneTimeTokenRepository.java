package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.OneTimeToken;
import com.healthcare.activitytracker.model.enums.TokenPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

  Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

  /**
   * Marks every still-active token of the given purpose for a user as used. Called before issuing a
   * fresh token so a user never has more than one live verification/reset token at a time.
   */
  @Modifying
  @Query(
      "UPDATE OneTimeToken t SET t.used = true "
          + "WHERE t.user.id = :userId AND t.purpose = :purpose AND t.used = false")
  int invalidateActiveTokens(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose);

  /** Clean up expired tokens. */
  @Modifying
  @Query("DELETE FROM OneTimeToken t WHERE t.expiresAt < :now")
  int deleteExpired(@Param("now") LocalDateTime now);

  @Modifying
  @Query("DELETE FROM OneTimeToken t WHERE t.user.id = :userId")
  int deleteByUserId(@Param("userId") UUID userId);
}
