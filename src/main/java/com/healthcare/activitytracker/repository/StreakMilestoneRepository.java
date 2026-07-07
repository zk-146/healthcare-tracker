package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.StreakMilestone;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreakMilestoneRepository extends JpaRepository<StreakMilestone, UUID> {
  boolean existsByUserIdAndMilestoneDays(UUID userId, Integer milestoneDays);

  /** Delete all streak milestones for a user (account deletion). */
  @Modifying
  @Query("DELETE FROM StreakMilestone sm WHERE sm.user.id = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);
}
