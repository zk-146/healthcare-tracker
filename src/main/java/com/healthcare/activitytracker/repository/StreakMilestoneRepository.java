package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.StreakMilestone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreakMilestoneRepository extends JpaRepository<StreakMilestone, UUID> {
  boolean existsByUserIdAndMilestoneDays(UUID userId, Integer milestoneDays);

  List<StreakMilestone> findByUserIdOrderByMilestoneDaysDesc(UUID userId);

  /** Delete all streak milestones for a user (e.g. account erasure). */
  @Modifying
  @Query("DELETE FROM StreakMilestone s WHERE s.user.id = :userId")
  int deleteByUserId(@Param("userId") UUID userId);
}
