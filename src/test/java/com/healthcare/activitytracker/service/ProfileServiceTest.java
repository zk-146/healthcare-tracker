package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.ProfileResponse;
import com.healthcare.activitytracker.model.dto.ProfileUpdateRequest;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.repository.ActivityRepository;
import com.healthcare.activitytracker.repository.DeepSeekConnectionRepository;
import com.healthcare.activitytracker.repository.RefreshTokenRepository;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ActivityRepository activityRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private StreakMilestoneRepository streakMilestoneRepository;
  @Mock private DeepSeekConnectionRepository deepSeekConnectionRepository;

  private ProfileService profileService;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    profileService =
        new ProfileService(
            userRepository,
            activityRepository,
            refreshTokenRepository,
            streakMilestoneRepository,
            deepSeekConnectionRepository);
  }

  private User buildUser() {
    return User.builder()
        .id(userId)
        .email("jane@example.com")
        .passwordHash("hashed")
        .fullName("Jane Doe")
        .dateOfBirth(LocalDate.of(1990, 1, 1))
        .gender("female")
        .heightCm(170.0)
        .weightKg(65.0)
        .createdAt(LocalDateTime.now().minusDays(30))
        .updatedAt(LocalDateTime.now().minusDays(1))
        .build();
  }

  @Test
  void getProfile_returnsMappedResponseWhenUserExists() {
    User user = buildUser();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    ProfileResponse response = profileService.getProfile(userId);

    assertThat(response.getId()).isEqualTo(userId);
    assertThat(response.getEmail()).isEqualTo("jane@example.com");
    assertThat(response.getFullName()).isEqualTo("Jane Doe");
    assertThat(response.getHeightCm()).isEqualTo(170.0);
    assertThat(response.getWeightKg()).isEqualTo(65.0);
  }

  @Test
  void getProfile_throwsWhenUserNotFound() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.getProfile(userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");
  }

  @Test
  void updateProfile_appliesOnlyNonNullFields() {
    User user = buildUser();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProfileUpdateRequest request = new ProfileUpdateRequest();
    request.setFullName("Jane Updated");
    request.setHeightCm(175.0);
    // gender, dateOfBirth, weightKg left null -> should stay unchanged

    ProfileResponse response = profileService.updateProfile(userId, request);

    assertThat(response.getFullName()).isEqualTo("Jane Updated");
    assertThat(response.getHeightCm()).isEqualTo(175.0);
    assertThat(response.getGender()).isEqualTo("female");
    assertThat(response.getWeightKg()).isEqualTo(65.0);
    assertThat(response.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
  }

  @Test
  void updateProfile_throwsWhenUserNotFound() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());
    ProfileUpdateRequest request = new ProfileUpdateRequest();

    assertThatThrownBy(() -> profileService.updateProfile(userId, request))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void deleteAccount_removesDependentDataThenUser() {
    User user = buildUser();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(refreshTokenRepository.deleteAllByUserId(userId)).thenReturn(2);
    when(streakMilestoneRepository.deleteAllByUserId(userId)).thenReturn(3);
    when(activityRepository.deleteAllByUserId(userId)).thenReturn(10);

    profileService.deleteAccount(userId);

    verify(refreshTokenRepository).deleteAllByUserId(userId);
    verify(streakMilestoneRepository).deleteAllByUserId(userId);
    verify(activityRepository).deleteAllByUserId(userId);
    verify(deepSeekConnectionRepository).deleteAllByUserId(userId);
    verify(userRepository).delete(user);
  }

  @Test
  void deleteAccount_throwsWhenUserNotFoundAndDeletesNothing() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> profileService.deleteAccount(userId))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(refreshTokenRepository, never()).deleteAllByUserId(any());
    verify(streakMilestoneRepository, never()).deleteAllByUserId(any());
    verify(activityRepository, never()).deleteAllByUserId(any());
    verify(deepSeekConnectionRepository, never()).deleteAllByUserId(any());
    verify(userRepository, never()).delete(any());
  }
}
