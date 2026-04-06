package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.entity.StreakMilestone;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for {@link ActivityCreatedEvent}.
 *
 * <p>Detects streak milestones asynchronously — the POST /api/v1/activities request returns
 * immediately and this runs on the Kafka consumer thread.
 */
@Service
public class ActivityEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(ActivityEventConsumer.class);

  private static final Set<Integer> MILESTONE_THRESHOLDS = Set.of(3, 7, 14, 30, 60, 100, 365);

  private final SummaryService summaryService;
  private final StreakMilestoneRepository milestoneRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;

  public ActivityEventConsumer(
      SummaryService summaryService,
      StreakMilestoneRepository milestoneRepository,
      UserRepository userRepository,
      NotificationService notificationService) {
    this.summaryService = summaryService;
    this.milestoneRepository = milestoneRepository;
    this.userRepository = userRepository;
    this.notificationService = notificationService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.activity-events}",
      groupId = "${app.kafka.consumer.group-id}")
  @Transactional
  public void onActivityCreated(
      @Payload ActivityCreatedEvent event,
      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
      @Header(KafkaHeaders.OFFSET) long offset) {

    log.info(
        "Consumed ActivityCreatedEvent eventId={} userId={} activityId={} type={} partition={}"
            + " offset={}",
        event.getEventId(),
        event.getUserId(),
        event.getActivityId(),
        event.getActivityType(),
        partition,
        offset);

    int streak = summaryService.getCurrentStreak(event.getUserId(), ZoneOffset.UTC);

    if (!MILESTONE_THRESHOLDS.contains(streak)) {
      return;
    }

    if (milestoneRepository.existsByUserIdAndMilestoneDays(event.getUserId(), streak)) {
      log.debug("User {} already at milestone {} — skipping", event.getUserId(), streak);
      return;
    }

    User user =
        userRepository
            .findById(event.getUserId())
            .orElseThrow(() -> new IllegalStateException("User vanished: " + event.getUserId()));

    StreakMilestone milestone =
        StreakMilestone.builder()
            .user(user)
            .milestoneDays(streak)
            .achievedAt(LocalDateTime.now())
            .triggeringActivityId(event.getActivityId())
            .build();

    milestoneRepository.save(milestone);

    log.info(
        "MILESTONE REACHED userId={} streakDays={} triggeringActivityId={}",
        event.getUserId(),
        streak,
        event.getActivityId());

    notificationService.notifyMilestone(user, streak);
  }
}
