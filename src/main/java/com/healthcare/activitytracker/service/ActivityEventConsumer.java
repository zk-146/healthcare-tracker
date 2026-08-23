package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.entity.StreakMilestone;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.event.ActivityCreatedEvent;
import com.healthcare.activitytracker.repository.StreakMilestoneRepository;
import com.healthcare.activitytracker.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
 *
 * <p><strong>Timezone note:</strong> milestone streaks are computed with UTC day boundaries. The
 * event does not carry the user's timezone (the X-User-Timezone header only exists on summary
 * reads), so a user far from UTC may see a summary streak that differs by one from the milestone
 * engine around midnight. If exact agreement is required, persist a timezone on the user profile
 * and propagate it through {@link ActivityCreatedEvent}.
 */
@Service
public class ActivityEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(ActivityEventConsumer.class);

  /** Ascending — the order is relied upon when picking the highest threshold crossed. */
  private static final List<Integer> MILESTONE_THRESHOLDS = List.of(3, 7, 14, 30, 60, 100, 365);

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
        "Consumed ActivityCreatedEvent eventId={} userId={} activityId={} type={} partition={} offset={}",
        event.getEventId(),
        event.getUserId(),
        event.getActivityId(),
        event.getActivityType(),
        partition,
        offset);

    int streak = summaryService.getCurrentStreak(event.getUserId(), ZoneOffset.UTC);

    // Every threshold at or below the current streak has been reached. Matching on
    // streak *equality* would silently skip thresholds whenever the streak jumps
    // rather than advancing one day at a time — which is exactly what a bulk CSV
    // import or the Google Health initial backfill does. A 31-day backfill would
    // award nothing at all, because 31 is not itself a threshold.
    List<Integer> newlyReached =
        MILESTONE_THRESHOLDS.stream()
            .filter(threshold -> threshold <= streak)
            .filter(
                threshold ->
                    !milestoneRepository.existsByUserIdAndMilestoneDays(
                        event.getUserId(), threshold))
            .toList();

    if (newlyReached.isEmpty()) {
      return;
    }

    User user =
        userRepository
            .findById(event.getUserId())
            .orElseThrow(() -> new IllegalStateException("User vanished: " + event.getUserId()));

    LocalDateTime achievedAt = LocalDateTime.now(ZoneOffset.UTC);
    for (Integer threshold : newlyReached) {
      milestoneRepository.save(
          StreakMilestone.builder()
              .user(user)
              .milestoneDays(threshold)
              .achievedAt(achievedAt)
              .triggeringActivityId(event.getActivityId())
              .build());

      log.info(
          "MILESTONE REACHED userId={} streakDays={} triggeringActivityId={}",
          event.getUserId(),
          threshold,
          event.getActivityId());
    }

    // Notify once, for the highest threshold crossed. Backfilling past 3/7/14/30
    // should congratulate the user on 30, not send four notifications. In normal
    // day-to-day use only one threshold is ever new, so this is unchanged.
    int highest = newlyReached.get(newlyReached.size() - 1);
    notificationService.sendMilestoneNotification(user, highest, event.getActivityId());
  }
}
