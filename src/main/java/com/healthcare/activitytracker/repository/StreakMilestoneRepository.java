package com.healthcare.activitytracker.repository;

import com.healthcare.activitytracker.model.entity.StreakMilestone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakMilestoneRepository extends JpaRepository<StreakMilestone, UUID> {
  boolean existsByUserIdAndMilestoneDays(UUID userId, Integer milestoneDays);

  List<StreakMilestone> findByUserIdOrderByMilestoneDaysDesc(UUID userId);
}
