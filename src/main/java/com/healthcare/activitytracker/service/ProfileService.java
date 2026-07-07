package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.ProfileResponse;
import com.healthcare.activitytracker.model.dto.ProfileUpdateRequest;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.repository.ActivityRepository;
import com.healthcare.activitytracker.repository.RefreshTokenRepository;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

  private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

  private final UserRepository userRepository;
  private final ActivityRepository activityRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final StreakMilestoneRepository streakMilestoneRepository;

  public ProfileService(
      UserRepository userRepository,
      ActivityRepository activityRepository,
      RefreshTokenRepository refreshTokenRepository,
      StreakMilestoneRepository streakMilestoneRepository) {
    this.userRepository = userRepository;
    this.activityRepository = activityRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.streakMilestoneRepository = streakMilestoneRepository;
  }

  /**
   * Retrieves the profile for the given user.
   *
   * @param userId the authenticated user's ID
   * @return the user's profile details
   * @throws com.healthcare.activitytracker.exception.ResourceNotFoundException if the user does not
   *     exist
   */
  @Transactional(readOnly = true)
  public ProfileResponse getProfile(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    return toResponse(user);
  }

  /**
   * Applies a partial update to the user's profile. Only non-null fields in the request are
   * written; null fields are left unchanged.
   *
   * @param userId the authenticated user's ID
   * @param request the profile fields to update
   * @return the updated profile details
   * @throws com.healthcare.activitytracker.exception.ResourceNotFoundException if the user does not
   *     exist
   */
  @Transactional
  public ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (request.getFullName() != null) user.setFullName(request.getFullName());
    if (request.getDateOfBirth() != null) user.setDateOfBirth(request.getDateOfBirth());
    if (request.getGender() != null) user.setGender(request.getGender());
    if (request.getHeightCm() != null) user.setHeightCm(request.getHeightCm());
    if (request.getWeightKg() != null) user.setWeightKg(request.getWeightKg());

    user = userRepository.save(user);
    return toResponse(user);
  }

  /**
   * Permanently deletes the user's account and all associated data: activities, refresh tokens, and
   * streak milestones. Irreversible (GDPR/right-to-erasure).
   *
   * @param userId the authenticated user's ID
   * @throws com.healthcare.activitytracker.exception.ResourceNotFoundException if the user does not
   *     exist
   */
  @Transactional
  public void deleteAccount(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    int tokens = refreshTokenRepository.deleteAllByUserId(userId);
    int milestones = streakMilestoneRepository.deleteAllByUserId(userId);
    int activities = activityRepository.deleteAllByUserId(userId);
    userRepository.delete(user);

    log.info(
        "Account deleted for user {}: {} activities, {} refresh tokens, {} milestones removed",
        userId,
        activities,
        tokens,
        milestones);
  }

  private ProfileResponse toResponse(User user) {
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
}
