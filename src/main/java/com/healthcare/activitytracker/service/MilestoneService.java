package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.dto.MilestoneProgressResponse;
import com.healthcare.activitytracker.model.dto.MilestoneResponse;
import com.healthcare.activitytracker.model.entity.StreakMilestone;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import com.healthcare.activitytracker.util.MilestoneThresholds;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MilestoneService {

  private final StreakMilestoneRepository milestoneRepository;
  private final SummaryService summaryService;

  public MilestoneService(
      StreakMilestoneRepository milestoneRepository, SummaryService summaryService) {
    this.milestoneRepository = milestoneRepository;
    this.summaryService = summaryService;
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

  /**
   * Progress toward the next unearned milestone. Unlike {@link #getMilestones}, which can only
   * report rows that exist, this reports the rung the user has not reached yet.
   *
   * @param userId the authenticated user's ID
   * @param zone the caller's timezone, used for streak boundary calculations
   */
  public MilestoneProgressResponse getProgress(UUID userId, ZoneId zone) {
    int currentStreak = summaryService.getCurrentStreak(userId, zone);
    Integer nextThreshold = MilestoneThresholds.next(currentStreak).orElse(null);

    return MilestoneProgressResponse.builder()
        .currentStreak(currentStreak)
        .nextThreshold(nextThreshold)
        .daysRemaining(nextThreshold == null ? null : nextThreshold - currentStreak)
        .progress(nextThreshold == null ? 1.0 : Math.max(0, currentStreak) / (double) nextThreshold)
        .ladder(MilestoneThresholds.ALL)
        .build();
  }

  private static MilestoneResponse toResponse(StreakMilestone milestone) {
    return MilestoneResponse.builder()
        .milestoneDays(milestone.getMilestoneDays())
        .achievedAt(milestone.getAchievedAt())
        .build();
  }
}
