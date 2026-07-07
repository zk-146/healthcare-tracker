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
}
