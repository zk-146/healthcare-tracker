package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.ActivityRequest;
import com.healthcare.activitytracker.model.dto.ActivityResponse;
import com.healthcare.activitytracker.model.entity.Activity;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.ActivitySource;
import com.healthcare.activitytracker.model.enums.ActivityType;
import com.healthcare.activitytracker.model.enums.AuditEventType;
import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import com.healthcare.activitytracker.repository.ActivityRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

  private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

  /** Hard cap on page size enforced at the service layer (defence-in-depth). */
  @Value("${spring.data.web.pageable.max-page-size:100}")
  private int maxPageSize;

  private final ActivityRepository activityRepository;
  private final UserRepository userRepository;
  private final ActivityEventPublisher activityEventPublisher;
  private final AuditService auditService;

  public ActivityService(
      ActivityRepository activityRepository,
      UserRepository userRepository,
      ActivityEventPublisher activityEventPublisher,
      AuditService auditService) {
    this.activityRepository = activityRepository;
    this.userRepository = userRepository;
    this.activityEventPublisher = activityEventPublisher;
    this.auditService = auditService;
  }

  /**
   * Creates a new activity record for the given user and publishes an {@code ACTIVITY_CREATED}
   * event to Kafka.
   *
   * @param userId the authenticated user's ID
   * @param request the activity details to persist
   * @return the persisted activity as a response DTO
   * @throws com.healthcare.activitytracker.exception.ResourceNotFoundException if the user does not
   *     exist
   */
  @Transactional
  public ActivityResponse createActivity(UUID userId, ActivityRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Activity activity =
        Activity.builder()
            .user(user)
            .activityType(request.getActivityType())
            .source(request.getSource())
            .deviceId(request.getDeviceId())
            .startedAt(request.getStartedAt())
            .endedAt(request.getEndedAt())
            .durationMinutes(request.getDurationMinutes())
            .distanceKm(request.getDistanceKm())
            .caloriesBurned(request.getCaloriesBurned())
            .heartRateAvg(request.getHeartRateAvg())
            .steps(request.getSteps())
            .notes(request.getNotes())
            .build();

    activity = activityRepository.saveAndFlush(activity);
    log.info(
        "Activity created: {} [type={}, source={}]",
        activity.getId(),
        activity.getActivityType(),
        activity.getSource());

    activityEventPublisher.publishActivityCreated(toEvent(activity));
    auditService.record(userId, AuditEventType.ACTIVITY_CREATE, "activityId=" + activity.getId());

    return toResponse(activity);
  }

  private ActivityCreatedEvent toEvent(Activity activity) {
    return ActivityCreatedEvent.builder()
        .eventId(UUID.randomUUID())
        .eventType("ACTIVITY_CREATED")
        .occurredAt(LocalDateTime.now())
        .activityId(activity.getId())
        .userId(activity.getUser().getId())
        .activityType(activity.getActivityType())
        .source(activity.getSource())
        .durationMinutes(activity.getDurationMinutes())
        .caloriesBurned(activity.getCaloriesBurned())
        .distanceKm(activity.getDistanceKm())
        .startedAt(activity.getStartedAt())
        .build();
  }

  /**
   * Returns a paginated list of activities for the given user, with optional filters. Page size is
   * capped at {@code spring.data.web.pageable.max-page-size} regardless of the requested value.
   *
   * @param userId the authenticated user's ID
   * @param type optional filter by activity type
   * @param source optional filter by activity source
   * @param from optional inclusive start date (activities on or after this date)
   * @param to optional inclusive end date (activities on or before this date)
   * @param pageable pagination and sort parameters
   * @return a page of matching activities ordered by {@code startedAt} descending
   */
  @Transactional(readOnly = true)
  public Page<ActivityResponse> getActivities(
      UUID userId,
      ActivityType type,
      ActivitySource source,
      LocalDate from,
      LocalDate to,
      Pageable pageable) {
    // Cap page size — defence-in-depth in case Spring's max-page-size config is bypassed
    if (pageable.getPageSize() > maxPageSize) {
      pageable = PageRequest.of(pageable.getPageNumber(), maxPageSize, pageable.getSort());
    }
    log.debug(
        "Listing activities for user {} [type={}, source={}, from={}, to={}]",
        userId,
        type,
        source,
        from,
        to);
    LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
    LocalDateTime toDt = to != null ? to.atTime(LocalTime.MAX) : null;
    return activityRepository
        .findByFilters(userId, fromDt, toDt, type, source, pageable)
        .map(this::toResponse);
  }

  /**
   * Retrieves a single activity by ID, scoped to the requesting user.
   *
   * @param userId the authenticated user's ID
   * @param activityId the activity to fetch
   * @return the activity as a response DTO
   * @throws com.healthcare.activitytracker.exception.ResourceNotFoundException if the activity does
   *     not exist or belongs to a different user
   */
  @Transactional(readOnly = true)
  public ActivityResponse getActivity(UUID userId, UUID activityId) {
    log.debug("Fetching activity {} for user {}", activityId, userId);
    Activity activity =
        activityRepository
            .findByIdAndUserId(activityId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));
    return toResponse(activity);
  }

  /**
   * Replaces all mutable fields of an existing activity with values from the request.
   *
   * @param userId the authenticated user's ID
   * @param activityId the activity to update
   * @param request the new activity details
   * @return the updated activity as a response DTO
   * @throws com.healthcare.activitytracker.exception.ResourceNotFoundException if the activity does
   *     not exist or belongs to a different user
   */
  @Transactional
  public ActivityResponse updateActivity(UUID userId, UUID activityId, ActivityRequest request) {
    Activity activity =
        activityRepository
            .findByIdAndUserId(activityId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));

    activity.setActivityType(request.getActivityType());
    activity.setSource(request.getSource());
    activity.setDeviceId(request.getDeviceId());
    activity.setStartedAt(request.getStartedAt());
    activity.setEndedAt(request.getEndedAt());
    activity.setDurationMinutes(request.getDurationMinutes());
    activity.setDistanceKm(request.getDistanceKm());
    activity.setCaloriesBurned(request.getCaloriesBurned());
    activity.setHeartRateAvg(request.getHeartRateAvg());
    activity.setSteps(request.getSteps());
    activity.setNotes(request.getNotes());

    activity = activityRepository.save(activity);
    log.info("Activity updated: {}", activity.getId());
    auditService.record(userId, AuditEventType.ACTIVITY_UPDATE, "activityId=" + activityId);

    return toResponse(activity);
  }

  /**
   * Permanently deletes an activity record, scoped to the requesting user.
   *
   * @param userId the authenticated user's ID
   * @param activityId the activity to delete
   * @throws com.healthcare.activitytracker.exception.ResourceNotFoundException if the activity does
   *     not exist or belongs to a different user
   */
  @Transactional
  public void deleteActivity(UUID userId, UUID activityId) {
    Activity activity =
        activityRepository
            .findByIdAndUserId(activityId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));

    activityRepository.delete(activity);
    log.info("Activity deleted: {}", activityId);
    auditService.record(userId, AuditEventType.ACTIVITY_DELETE, "activityId=" + activityId);
  }

  private ActivityResponse toResponse(Activity activity) {
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
}
