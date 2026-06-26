package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.ActivityResponse;
import com.healthcare.activitytracker.model.dto.ProfileResponse;
import com.healthcare.activitytracker.model.dto.StreakMilestoneResponse;
import com.healthcare.activitytracker.model.dto.UserDataExportResponse;
import com.healthcare.activitytracker.model.entity.Activity;
import com.healthcare.activitytracker.model.entity.StreakMilestone;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.AuditEventType;
import com.healthcare.activitytracker.repository.ActivityRepository;
import com.healthcare.activitytracker.repository.OneTimeTokenRepository;
import com.healthcare.activitytracker.repository.RefreshTokenRepository;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account-wide data operations supporting compliance obligations: portable data export (GDPR data
 * portability) and account erasure (right to be forgotten).
 */
@Service
public class AccountService {

  private static final Logger log = LoggerFactory.getLogger(AccountService.class);

  private final UserRepository userRepository;
  private final ActivityRepository activityRepository;
  private final StreakMilestoneRepository streakMilestoneRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final OneTimeTokenRepository oneTimeTokenRepository;
  private final AuthService authService;
  private final AuditService auditService;

  public AccountService(
      UserRepository userRepository,
      ActivityRepository activityRepository,
      StreakMilestoneRepository streakMilestoneRepository,
      RefreshTokenRepository refreshTokenRepository,
      OneTimeTokenRepository oneTimeTokenRepository,
      AuthService authService,
      AuditService auditService) {
    this.userRepository = userRepository;
    this.activityRepository = activityRepository;
    this.streakMilestoneRepository = streakMilestoneRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.oneTimeTokenRepository = oneTimeTokenRepository;
    this.authService = authService;
    this.auditService = auditService;
  }

  /** Assembles a complete, portable copy of the user's data. */
  @Transactional(readOnly = true)
  public UserDataExportResponse exportUserData(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    List<ActivityResponse> activities =
        activityRepository.findByUserIdOrderByStartedAtDesc(userId).stream()
            .map(this::toActivityResponse)
            .toList();

    List<StreakMilestoneResponse> milestones =
        streakMilestoneRepository.findByUserIdOrderByMilestoneDaysDesc(userId).stream()
            .map(this::toMilestoneResponse)
            .toList();

    auditService.record(
        userId,
        AuditEventType.DATA_EXPORT,
        "activities=" + activities.size() + " milestones=" + milestones.size());

    return UserDataExportResponse.builder()
        .exportedAt(Instant.now())
        .profile(toProfileResponse(user))
        .activities(activities)
        .streakMilestones(milestones)
        .build();
  }

  /**
   * Permanently erases the user and all data referencing them (right to be forgotten). Audited
   * before deletion so the audit row's {@code user_id} is recorded while it is still meaningful.
   */
  @Transactional
  public void deleteAccount(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    auditService.record(userId, AuditEventType.ACCOUNT_DELETE);

    // Revoke active sessions, then remove every row referencing the user before the user itself
    // so foreign-key constraints are satisfied.
    authService.revokeAllUserTokens(userId);
    oneTimeTokenRepository.deleteByUserId(userId);
    refreshTokenRepository.deleteByUserId(userId);
    streakMilestoneRepository.deleteByUserId(userId);
    activityRepository.deleteByUserId(userId);
    userRepository.delete(user);

    log.info("Account erased for userId={}", userId);
  }

  private ProfileResponse toProfileResponse(User user) {
    return ProfileResponse.builder()
        .id(user.getId())
        .email(user.getEmail())
        .fullName(user.getFullName())
        .dateOfBirth(user.getDateOfBirth())
        .gender(user.getGender())
        .heightCm(user.getHeightCm())
        .weightKg(user.getWeightKg())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }

  private ActivityResponse toActivityResponse(Activity activity) {
    return ActivityResponse.builder()
        .id(activity.getId())
        .activityType(activity.getActivityType())
        .source(activity.getSource())
        .deviceId(activity.getDeviceId())
        .startedAt(activity.getStartedAt())
        .endedAt(activity.getEndedAt())
        .durationMinutes(activity.getDurationMinutes())
        .distanceKm(activity.getDistanceKm())
        .caloriesBurned(activity.getCaloriesBurned())
        .heartRateAvg(activity.getHeartRateAvg())
        .steps(activity.getSteps())
        .notes(activity.getNotes())
        .createdAt(activity.getCreatedAt())
        .updatedAt(activity.getUpdatedAt())
        .build();
  }

  private StreakMilestoneResponse toMilestoneResponse(StreakMilestone milestone) {
    return StreakMilestoneResponse.builder()
        .id(milestone.getId())
        .milestoneDays(milestone.getMilestoneDays())
        .achievedAt(milestone.getAchievedAt())
        .triggeringActivityId(milestone.getTriggeringActivityId())
        .build();
  }
}
