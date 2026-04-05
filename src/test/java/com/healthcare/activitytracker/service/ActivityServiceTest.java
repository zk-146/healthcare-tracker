package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.ActivityRequest;
import com.healthcare.activitytracker.model.dto.ActivityResponse;
import com.healthcare.activitytracker.model.entity.Activity;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.repository.ActivityRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

  @Mock private ActivityRepository activityRepository;
  @Mock private UserRepository userRepository;
  @Mock private ActivityEventPublisher activityEventPublisher;

  private ActivityService activityService;

  private final UUID userId = UUID.randomUUID();
  private final UUID activityId = UUID.randomUUID();

  @BeforeEach
  void setUp() throws Exception {
    activityService =
        new ActivityService(activityRepository, userRepository, activityEventPublisher);
    // Inject maxPageSize (normally set by @Value) so PageRequest.of() doesn't get size 0
    java.lang.reflect.Field f = ActivityService.class.getDeclaredField("maxPageSize");
    f.setAccessible(true);
    f.set(activityService, 100);
  }

  private User testUser() {
    return User.builder()
        .id(userId)
        .email("test@example.com")
        .fullName("Test")
        .passwordHash("hash")
        .build();
  }

  private Activity testActivity() {
    return Activity.builder()
        .id(activityId)
        .user(testUser())
        .activityType(ActivityType.RUNNING)
        .source(ActivitySource.MANUAL)
        .startedAt(LocalDateTime.now().minusHours(1))
        .build();
  }

  private ActivityRequest testRequest() {
    ActivityRequest req = new ActivityRequest();
    req.setActivityType(ActivityType.RUNNING);
    req.setSource(ActivitySource.MANUAL);
    req.setStartedAt(LocalDateTime.now().minusHours(1));
    return req;
  }

  @Test
  void createActivity_savesAndReturnsResponse() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser()));
    when(activityRepository.save(any(Activity.class)))
        .thenAnswer(
            inv -> {
              Activity a = inv.getArgument(0);
              a =
                  Activity.builder()
                      .id(activityId)
                      .user(a.getUser())
                      .activityType(a.getActivityType())
                      .source(a.getSource())
                      .startedAt(a.getStartedAt())
                      .build();
              return a;
            });

    ActivityResponse response = activityService.createActivity(userId, testRequest());
    assertThat(response.getId()).isEqualTo(activityId);
    assertThat(response.getActivityType()).isEqualTo(ActivityType.RUNNING);
  }

  @Test
  void createActivity_throwsNotFound_whenUserMissing() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> activityService.createActivity(userId, testRequest()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void getActivity_returnsResponse_whenOwnerMatches() {
    when(activityRepository.findByIdAndUserId(activityId, userId))
        .thenReturn(Optional.of(testActivity()));

    ActivityResponse response = activityService.getActivity(userId, activityId);
    assertThat(response.getId()).isEqualTo(activityId);
  }

  @Test
  void getActivity_throwsNotFound_whenOwnerMismatch() {
    when(activityRepository.findByIdAndUserId(activityId, userId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> activityService.getActivity(userId, activityId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void deleteActivity_deletesSuccessfully() {
    when(activityRepository.findByIdAndUserId(activityId, userId))
        .thenReturn(Optional.of(testActivity()));
    activityService.deleteActivity(userId, activityId);
    verify(activityRepository, times(1)).delete(any(Activity.class));
  }

  @Test
  void getActivities_returnsPagedResults() {
    PageRequest pageable = PageRequest.of(0, 20);
    Page<Activity> activityPage = new PageImpl<>(List.of(testActivity()));
    when(activityRepository.findByFilters(eq(userId), any(), any(), any(), any(), eq(pageable)))
        .thenReturn(activityPage);

    Page<ActivityResponse> result =
        activityService.getActivities(userId, null, null, null, null, pageable);
    assertThat(result.getTotalElements()).isEqualTo(1);
  }
}
