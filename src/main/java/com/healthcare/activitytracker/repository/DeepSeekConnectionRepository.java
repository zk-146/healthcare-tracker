package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.DeepSeekConnection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeepSeekConnectionRepository extends JpaRepository<DeepSeekConnection, UUID> {

  Optional<DeepSeekConnection> findByUserId(UUID userId);

  /** Removes the user's connection, if any (account deletion, or an explicit disconnect). */
  @Modifying
  @Query("DELETE FROM DeepSeekConnection c WHERE c.user.id = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);
}
