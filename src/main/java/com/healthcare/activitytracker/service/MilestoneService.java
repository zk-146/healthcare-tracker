package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.dto.MilestoneResponse;
import com.healthcare.activitytracker.model.entity.StreakMilestone;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MilestoneService {

  private final StreakMilestoneRepository milestoneRepository;

  public MilestoneService(StreakMilestoneRepository milestoneRepository) {
    this.milestoneRepository = milestoneRepository;
  }

  /**
   * Every streak milestone the user has earned, longest streak first.
   *
   * @param userId the authenticated user's ID
   */
  public List<MilestoneResponse> getMilestones(UUID userId) {
    return milestoneRepository.findByUserIdOrderByMilestoneDaysDesc(userId).stream()
        .map(MilestoneService::toResponse)
        .toList();
  }

  private static MilestoneResponse toResponse(StreakMilestone milestone) {
    return MilestoneResponse.builder()
        .milestoneDays(milestone.getMilestoneDays())
        .achievedAt(milestone.getAchievedAt())
        .build();
  }
}
