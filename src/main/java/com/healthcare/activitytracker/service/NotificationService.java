package com.healthcare.activitytracker.service;

import com.healthcare.activitytracker.model.entity.User;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  public void sendMilestoneNotification(User user, int streakDays, UUID triggeringActivityId) {
    // The email address is deliberately NOT logged (HIPAA/PII policy: identifiers only).
    // A real delivery integration should resolve the address at send time.
    log.info(
        "NOTIFICATION userId={} type=STREAK_MILESTONE streakDays={} triggeringActivityId={}",
        user.getId(),
        streakDays,
        triggeringActivityId);
  }

  /**
   * Prompts the owner to re-authorize an external integration. Sent when a refresh token is
   * revoked/expired (in Google OAuth "Testing" mode this happens roughly every 7 days).
   */
  public void sendReconnectReminder(User user, String integration) {
    // Email deliberately not logged, matching the HIPAA/PII policy above.
    log.info(
        "NOTIFICATION userId={} type=RECONNECT_REQUIRED integration={}", user.getId(), integration);
  }
}
