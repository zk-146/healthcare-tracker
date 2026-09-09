package com.healthcare.activitytracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.activitytracker.exception.FieldValidationException;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
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

  private final ActivityTypeMapper activityTypeMapper = new ActivityTypeMapper();

  private ActivityService activityService;

  private final UUID userId = UUID.randomUUID();
  private final UUID activityId = UUID.randomUUID();

  @BeforeEach
  void setUp() throws Exception {
    activityService =
        new ActivityService(
            activityRepository,
            userRepository,
            activityEventPublisher,
            activityTypeMapper,
            new CalorieEstimator());
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
        .startedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
        .build();
  }

  /**
   * Built against a fixed clock (UTC), not the JVM default zone: every call site below passes
   * ZoneOffset.UTC as the validation zone, so a startedAt built from LocalDateTime.now() with no
   * explicit zone would fail validateNotFuture on any machine east of UTC -- exactly the class of
   * bug this fix exists to close.
   */
  private ActivityRequest testRequest() {
    ActivityRequest req = new ActivityRequest();
    req.setActivityType(ActivityType.RUNNING);
    req.setSource(ActivitySource.MANUAL);
    req.setStartedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
    return req;
  }

  @Test
  void createActivity_savesAndReturnsResponse() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser()));
    when(activityRepository.saveAndFlush(any(Activity.class)))
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

    ActivityResponse response =
        activityService.createActivity(userId, testRequest(), ZoneOffset.UTC);
    assertThat(response.getId()).isEqualTo(activityId);
    assertThat(response.getActivityType()).isEqualTo(ActivityType.RUNNING);
  }

  @Test
  void createActivity_throwsNotFound_whenUserMissing() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> activityService.createActivity(userId, testRequest(), ZoneOffset.UTC))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  /**
   * The regression this fix targets: a caller 14 hours ahead of UTC (e.g. Pacific/Kiritimati)
   * submits a time that is genuinely in their own past, but which still lands two hours ahead of
   * the bare UTC clock a naive {@code @PastOrPresent} (or any check against the server's own
   * default-zone clock) would have compared it to. Before this fix, this request was wrongly
   * rejected as "in the future".
   */
  @Test
  void createActivity_succeeds_whenStartedAtIsFutureUnderUtcButPastInCallerZone() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser()));
    when(activityRepository.saveAndFlush(any(Activity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ZoneId farAheadZone = ZoneOffset.ofHours(14);
    ActivityRequest req = testRequest();
    req.setStartedAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(2));

    ActivityResponse response = activityService.createActivity(userId, req, farAheadZone);
    assertThat(response).isNotNull();
  }

  @Test
  void createActivity_throwsFieldValidation_whenStartedAtIsFutureInCallerZone() {
    ActivityRequest req = testRequest();
    req.setStartedAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));

    assertThatThrownBy(() -> activityService.createActivity(userId, req, ZoneOffset.UTC))
        .isInstanceOf(FieldValidationException.class)
        .hasFieldOrPropertyWithValue("field", "startedAt")
        .hasMessage("Start time cannot be in the future");
    // Rejected before any persistence was attempted.
    verifyNoInteractions(userRepository, activityRepository);
  }

  @Test
  void createActivity_throwsFieldValidation_whenEndedAtIsFutureInCallerZone() {
    ActivityRequest req = testRequest();
    req.setEndedAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));

    assertThatThrownBy(() -> activityService.createActivity(userId, req, ZoneOffset.UTC))
        .isInstanceOf(FieldValidationException.class)
        .hasFieldOrPropertyWithValue("field", "endedAt")
        .hasMessage("End time cannot be in the future");
  }

  @Test
  void updateActivity_throwsFieldValidation_whenStartedAtIsFutureInCallerZone() {
    ActivityRequest req = testRequest();
    req.setStartedAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));

    assertThatThrownBy(
            () -> activityService.updateActivity(userId, activityId, req, ZoneOffset.UTC))
        .isInstanceOf(FieldValidationException.class)
        .hasFieldOrPropertyWithValue("field", "startedAt");
    // Rejected before the activity was even looked up.
    verifyNoInteractions(activityRepository);
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

  @Test
  void importWorkout_savesNewWorkoutAsIotActivityAndPublishesEvent() {
    when(activityRepository.existsByUserIdAndExternalId(userId, "rec-1")).thenReturn(false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser()));
    when(activityRepository.saveAndFlush(any(Activity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    com.healthcare.activitytracker.model.integration.ImportedWorkout workout =
        com.healthcare.activitytracker.model.integration.ImportedWorkout.builder()
            .externalId("rec-1")
            .rawType("RUN")
            .startedAt(LocalDateTime.now().minusHours(1))
            .distanceKm(5.0)
            .build();

    boolean imported = activityService.importWorkout(userId, workout, "fitbit-charge-6");

    assertThat(imported).isTrue();
    org.mockito.ArgumentCaptor<Activity> captor =
        org.mockito.ArgumentCaptor.forClass(Activity.class);
    verify(activityRepository).saveAndFlush(captor.capture());
    Activity saved = captor.getValue();
    assertThat(saved.getSource()).isEqualTo(ActivitySource.IOT);
    assertThat(saved.getActivityType()).isEqualTo(ActivityType.RUNNING);
    assertThat(saved.getDeviceId()).isEqualTo("fitbit-charge-6");
    assertThat(saved.getExternalId()).isEqualTo("rec-1");
    verify(activityEventPublisher).publishActivityCreated(any());
  }

  @Test
  void createActivity_estimatesCalories_whenClientOmitsThem() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser()));
    when(activityRepository.saveAndFlush(any(Activity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ActivityRequest req = testRequest();
    req.setDurationMinutes(30); // RUNNING (MET 9.8), default 70 kg → 360.2 kcal

    ActivityResponse response = activityService.createActivity(userId, req, ZoneOffset.UTC);
    assertThat(response.getCaloriesBurned()).isEqualTo(360.2);
  }

  @Test
  void createActivity_keepsClientCalories_whenProvided() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser()));
    when(activityRepository.saveAndFlush(any(Activity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ActivityRequest req = testRequest();
    req.setDurationMinutes(30);
    req.setCaloriesBurned(500.0);

    ActivityResponse response = activityService.createActivity(userId, req, ZoneOffset.UTC);
    assertThat(response.getCaloriesBurned()).isEqualTo(500.0);
  }

  @Test
  void createActivity_leavesCaloriesNull_whenNotEstimable() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(testUser()));
    when(activityRepository.saveAndFlush(any(Activity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ActivityResponse response =
        activityService.createActivity(userId, testRequest(), ZoneOffset.UTC);
    assertThat(response.getCaloriesBurned()).isNull();
  }

  @Test
  void updateActivity_estimatesCalories_whenClientOmitsThem() {
    when(activityRepository.findByIdAndUserId(activityId, userId))
        .thenReturn(Optional.of(testActivity()));
    when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

    ActivityRequest req = testRequest();
    req.setDurationMinutes(30);

    ActivityResponse response =
        activityService.updateActivity(userId, activityId, req, ZoneOffset.UTC);
    assertThat(response.getCaloriesBurned()).isEqualTo(360.2);
  }

  @Test
  void importWorkout_skipsDuplicateAndDoesNotPublish() {
    when(activityRepository.existsByUserIdAndExternalId(userId, "rec-dup")).thenReturn(true);

    com.healthcare.activitytracker.model.integration.ImportedWorkout workout =
        com.healthcare.activitytracker.model.integration.ImportedWorkout.builder()
            .externalId("rec-dup")
            .rawType("WALK")
            .startedAt(LocalDateTime.now())
            .build();

    boolean imported = activityService.importWorkout(userId, workout, "fitbit-charge-6");

    assertThat(imported).isFalse();
    verify(activityRepository, never()).saveAndFlush(any());
    verify(activityEventPublisher, never()).publishActivityCreated(any());
  }
}
