package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.exception.ResourceNotFoundException;
import com.healthcare.activitytracker.model.dto.NotificationResponse;
import com.healthcare.activitytracker.model.entity.Notification;
import com.healthcare.activitytracker.model.entity.User;
import com.healthcare.activitytracker.model.enums.NotificationType;
import com.healthcare.activitytracker.repository.NotificationRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages in-app notifications. Persists notifications to the database and logs placeholders for
 * external delivery channels (email, push) that can be wired in when third-party integrations are
 * added.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  /**
   * Creates and persists a streak-milestone notification for the given user. Logs placeholders for
   * email and push delivery; swap these for real integrations when ready.
   *
   * @param user the recipient
   * @param streakDays the streak length that triggered this milestone
   */
  @Transactional
  public void notifyMilestone(User user, int streakDays) {
    String title = milestoneTitle(streakDays);
    String body = milestoneBody(streakDays);

    Notification notification =
        Notification.builder()
            .user(user)
            .type(NotificationType.STREAK_MILESTONE)
            .title(title)
            .body(body)
            .build();

    notificationRepository.save(notification);

    log.info(
        "In-app notification saved userId={} streakDays={} title={}",
        user.getId(),
        streakDays,
        title);

    // Placeholder: replace with JavaMailSender / SES call when email is configured.
    log.debug(
        "[EMAIL-PLACEHOLDER] To: {} | Subject: {} | Body: {}", user.getEmail(), title, body);

    // Placeholder: replace with FCM/APNs call when push credentials are configured.
    log.debug("[PUSH-PLACEHOLDER] userId={} title={}", user.getId(), title);
  }

  /**
   * Returns a paginated list of notifications for the given user, newest first.
   *
   * @param userId the authenticated user's ID
   * @param pageable pagination parameters
   * @return page of notification DTOs
   */
  @Transactional(readOnly = true)
  public Page<NotificationResponse> getNotifications(UUID userId, Pageable pageable) {
    return notificationRepository
        .findByUserIdOrderByCreatedAtDesc(userId, pageable)
        .map(this::toResponse);
  }

  /**
   * Returns the count of unread notifications for the given user.
   *
   * @param userId the authenticated user's ID
   * @return number of unread notifications
   */
  @Transactional(readOnly = true)
  public long countUnread(UUID userId) {
    return notificationRepository.countByUserIdAndReadFalse(userId);
  }

  /**
   * Marks a single notification as read, scoped to the requesting user.
   *
   * @param userId the authenticated user's ID
   * @param notificationId the notification to mark
   * @throws ResourceNotFoundException if the notification does not exist or belongs to another user
   */
  @Transactional
  public void markRead(UUID userId, UUID notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
    notification.setRead(true);
    notificationRepository.save(notification);
  }

  /**
   * Marks all notifications for the given user as read.
   *
   * @param userId the authenticated user's ID
   */
  @Transactional
  public void markAllRead(UUID userId) {
    notificationRepository.markAllReadForUser(userId);
    log.debug("Marked all notifications read for userId={}", userId);
  }

  private NotificationResponse toResponse(Notification n) {
    return NotificationResponse.builder()
        .id(n.getId())
        .type(n.getType())
        .title(n.getTitle())
        .body(n.getBody())
        .read(n.isRead())
        .createdAt(n.getCreatedAt())
        .build();
  }

  private String milestoneTitle(int days) {
    return days + "-Day Streak!";
  }

  private String milestoneBody(int days) {
    return switch (days) {
      case 3 -> "You've logged activities for 3 consecutive days. Keep it up!";
      case 7 -> "One week of consistent activity — great work!";
      case 14 -> "Two weeks of dedication — you're building a habit!";
      case 30 -> "One month of consistent activity — incredible!";
      case 60 -> "Two months of dedication — you're unstoppable!";
      case 100 -> "A century of consecutive activity days — elite performance!";
      case 365 -> "One full year of activity — legendary achievement!";
      default -> "You've reached a " + days + "-day activity streak!";
    };
  }
}
